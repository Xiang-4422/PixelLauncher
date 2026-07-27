from __future__ import annotations

import io
import json
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

from tools import check_theme_token_coverage


# One-field foundation types keep fixtures small while still exercising every required group.
FOUNDATION_SOURCE = """
public data class PixelColorScheme(public val primary: Int)
public data class PixelTypographyTokens(public val body: Int)
public data class PixelSpacingTokens(public val small: Int)
public data class PixelSizeTokens(public val controlHeight: Int)
public data class PixelRadiusTokens(public val small: Int)
public data class PixelBorderTokens(public val thin: Int)
public data class PixelElevationTokens(public val low: Int)
public data class PixelMotionThemeData(public val feedback: Int)
public data class PixelLabelTokens(public val loading: String)
"""

# Canonical component inventory includes one focusable and one passive standard family.
COMPONENT_SOURCE = """
public data class PixelComponentTokens(
  public val button: PixelComponentColorTokens = PixelComponentColorTokens(),
  public val toast: PixelComponentColorTokens = PixelComponentColorTokens(),
) { public companion object { public val Default = PixelComponentTokens() } }
"""

# A passing runtime proves bindings, two color channels, geometry, focus, and all foundation fields.
COMPLETE_RUNTIME_SOURCE = """
fun build(theme: PixelThemeTokens) {
  val buttonTokens = PixelTheme.of(context).components.button
  consume(buttonTokens.resolveContainerColor(states, theme.colors))
  consume(buttonTokens.resolveContentColor(states, theme.colors))
  consume(buttonTokens.resolvePadding(theme.spacing))
  consume(withControlFocusIndicator(componentTokens = buttonTokens))
  val toastTokens = theme.components.toast
  consume(toastTokens.resolveContainerColor(states, theme.colors))
  consume(toastTokens.resolveContentColor(states, theme.colors))
  consume(toastTokens.resolveCornerRadius(theme.radii))
  consume(theme.colors.primary)
  consume(theme.typography.body)
  consume(theme.spacing.small)
  consume(theme.sizes.controlHeight)
  consume(theme.radii.small)
  consume(theme.borders.thin)
  consume(theme.elevations.low)
  consume(theme.motion.feedback)
  consume(theme.labels.loading)
}
"""


class ThemeTokenCoverageTest(unittest.TestCase):
    """Proves the M5 gate rejects textual false positives and disconnected token fields."""

    def write_fixture(
        self,
        root: Path,
        runtime_source: str,
        foundation_source: str = FOUNDATION_SOURCE,
        component_source: str = COMPONENT_SOURCE,
    ) -> tuple[Path, Path, Path]:
        """Write one isolated token inventory, foundation definition, runtime, and report path."""

        source_root = root / "main"
        source_root.mkdir()
        tokens = source_root / "PixelComponentTokens.kt"
        tokens.write_text(component_source, encoding="utf-8")
        (source_root / "Foundation.kt").write_text(foundation_source, encoding="utf-8")
        (source_root / "Runtime.kt").write_text(runtime_source, encoding="utf-8")
        report = root / "coverage.json"
        return source_root, tokens, report

    def run_fixture(
        self,
        source_root: Path,
        tokens: Path,
        report: Path,
    ) -> tuple[int, dict[str, object]]:
        """Run the scanner while suppressing expected failure output and return parsed evidence."""

        stderr = io.StringIO()
        stdout = io.StringIO()
        with redirect_stderr(stderr), redirect_stdout(stdout):
            exit_code = check_theme_token_coverage.main(
                [
                    "--tokens",
                    str(tokens),
                    "--source-root",
                    str(source_root),
                    "--report",
                    str(report),
                ],
            )
        payload = json.loads(report.read_text(encoding="utf-8"))
        return exit_code, payload

    def test_resolver_backed_component_and_field_coverage_passes(self) -> None:
        """A complete binding-to-resolver chain and every foundation field pass the gate."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source_root, tokens, report = self.write_fixture(root, COMPLETE_RUNTIME_SOURCE)

            exit_code, payload = self.run_fixture(source_root, tokens, report)

            self.assertEqual(0, exit_code)
            self.assertEqual("passed", payload["status"])
            self.assertEqual(2, payload["schemaVersion"])
            self.assertEqual(2, payload["coveredComponentTokenCount"])
            self.assertEqual(10, payload["coveredFoundationGroupCount"])
            self.assertIn("componentReferences", payload)
            self.assertIn("foundationReferences", payload)
            self.assertTrue(payload["componentFieldReferences"]["button"]["focusIndicator"])
            self.assertTrue(payload["foundationFieldReferences"]["borders"]["thin"])

    def test_conditional_component_alias_credits_each_runtime_family(self) -> None:
        """A parsed `when` alias credits the shared renderer to every selectable family."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            runtime_source = COMPLETE_RUNTIME_SOURCE.replace(
                "  val buttonTokens = PixelTheme.of(context).components.button\n",
                "  val selectedTokens = when (family) {\n"
                "    Family.Button -> theme.components.button\n"
                "    Family.Toast -> theme.components.toast\n"
                "  }\n",
            ).replace("buttonTokens", "selectedTokens").replace(
                "  val toastTokens = theme.components.toast\n"
                "  consume(toastTokens.resolveContainerColor(states, theme.colors))\n"
                "  consume(toastTokens.resolveContentColor(states, theme.colors))\n"
                "  consume(toastTokens.resolveCornerRadius(theme.radii))\n",
                "",
            )
            source_root, tokens, report = self.write_fixture(root, runtime_source)

            exit_code, payload = self.run_fixture(source_root, tokens, report)

            self.assertEqual(0, exit_code)
            self.assertEqual("passed", payload["status"])
            self.assertTrue(payload["componentBindings"]["button"])
            self.assertTrue(payload["componentBindings"]["toast"])
            self.assertTrue(payload["componentFieldReferences"]["button"]["containerColor"])
            self.assertTrue(payload["componentFieldReferences"]["toast"]["containerColor"])

    def test_conditional_family_text_outside_initializer_is_not_credited(self) -> None:
        """A later family reference cannot backfill a missing branch in a conditional alias."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            runtime_source = COMPLETE_RUNTIME_SOURCE.replace(
                "  val buttonTokens = PixelTheme.of(context).components.button\n",
                "  val selectedTokens = when (family) {\n"
                "    else -> theme.components.button\n"
                "  }\n",
            ).replace("buttonTokens", "selectedTokens").replace(
                "  val toastTokens = theme.components.toast\n"
                "  consume(toastTokens.resolveContainerColor(states, theme.colors))\n"
                "  consume(toastTokens.resolveContentColor(states, theme.colors))\n"
                "  consume(toastTokens.resolveCornerRadius(theme.radii))\n",
                "  consume(theme.components.toast)\n",
            )
            source_root, tokens, report = self.write_fixture(root, runtime_source)

            exit_code, payload = self.run_fixture(source_root, tokens, report)

            toast_reasons = {
                finding["reason"]
                for finding in payload["findings"]
                if finding["token"] == "toast"
            }
            self.assertEqual(1, exit_code)
            self.assertIn("NO_COMPONENT_TOKEN_BINDING", toast_reasons)
            self.assertEqual([], payload["componentBindings"]["toast"])

    def test_dead_foundation_field_fails_despite_group_text_and_comments(self) -> None:
        """An unread border field cannot pass through another border read or comment/string text."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            foundation_source = FOUNDATION_SOURCE.replace(
                "public data class PixelBorderTokens(public val thin: Int)",
                "public data class PixelBorderTokens(public val thin: Int, public val focus: Int)",
            )
            runtime_source = COMPLETE_RUNTIME_SOURCE.replace(
                "  consume(theme.borders.thin)\n",
                "  consume(theme.borders.thin)\n"
                "  // theme.borders.focus is documentation, not a consumer.\n"
                "  consume(\"theme.borders.focus\")\n",
            )
            source_root, tokens, report = self.write_fixture(
                root,
                runtime_source,
                foundation_source=foundation_source,
            )

            exit_code, payload = self.run_fixture(source_root, tokens, report)

            finding_keys = {
                (finding["category"], finding["token"], finding["reason"])
                for finding in payload["findings"]
            }
            self.assertEqual(1, exit_code)
            self.assertIn(
                ("foundation-field", "borders.focus", "NO_RUNTIME_FIELD_CONSUMER"),
                finding_keys,
            )
            self.assertEqual([], payload["foundationFieldReferences"]["borders"]["focus"])

    def test_reachable_typed_resolver_credits_foundation_field(self) -> None:
        """A typed resolver field read counts only when production code calls that resolver."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            foundation_source = FOUNDATION_SOURCE.replace(
                "public data class PixelBorderTokens(public val thin: Int)",
                "public data class PixelBorderTokens(public val thin: Int, public val focus: Int)\n"
                "public fun resolveFocus(tokens: PixelBorderTokens): Int = tokens.focus",
            )
            runtime_source = COMPLETE_RUNTIME_SOURCE.replace(
                "  consume(theme.borders.thin)\n",
                "  consume(theme.borders.thin)\n  consume(resolveFocus(theme.borders))\n",
            )
            source_root, tokens, report = self.write_fixture(
                root,
                runtime_source,
                foundation_source=foundation_source,
            )

            exit_code, payload = self.run_fixture(source_root, tokens, report)

            self.assertEqual(0, exit_code)
            self.assertTrue(payload["foundationFieldReferences"]["borders"]["focus"])
            focus_evidence = payload["foundationFieldEvidence"]["borders"]["focus"]
            self.assertTrue(focus_evidence["resolverReferences"])
            self.assertIn("resolveFocus", focus_evidence["resolverConsumers"])

    def test_kotlin_property_reference_credits_foundation_field(self) -> None:
        """A typed Kotlin property reference passed to a runtime resolver counts as a field read."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            runtime_source = COMPLETE_RUNTIME_SOURCE.replace(
                "  consume(theme.labels.loading)\n",
                "  consume(resolveLabel(PixelLabelTokens::loading))\n",
            )
            source_root, tokens, report = self.write_fixture(root, runtime_source)

            exit_code, payload = self.run_fixture(source_root, tokens, report)

            self.assertEqual(0, exit_code)
            label_evidence = payload["foundationFieldEvidence"]["labels"]["loading"]
            self.assertTrue(label_evidence["directReferences"])

    def test_kotlin_safe_call_credits_foundation_field(self) -> None:
        """A nullable provider's safe-call token read remains real runtime field consumption."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            runtime_source = COMPLETE_RUNTIME_SOURCE.replace(
                "  consume(theme.labels.loading)\n",
                "  consume(localizations?.labels?.loading)\n",
            )
            source_root, tokens, report = self.write_fixture(root, runtime_source)

            exit_code, payload = self.run_fixture(source_root, tokens, report)

            self.assertEqual(0, exit_code)
            label_evidence = payload["foundationFieldEvidence"]["labels"]["loading"]
            self.assertTrue(label_evidence["directReferences"])

    def test_unreachable_typed_resolver_does_not_hide_dead_field(self) -> None:
        """A resolver definition without a production call remains dead foundation evidence."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            foundation_source = FOUNDATION_SOURCE.replace(
                "public data class PixelBorderTokens(public val thin: Int)",
                "public data class PixelBorderTokens(public val thin: Int, public val focus: Int)\n"
                "public fun resolveFocus(tokens: PixelBorderTokens): Int = tokens.focus",
            )
            source_root, tokens, report = self.write_fixture(
                root,
                COMPLETE_RUNTIME_SOURCE,
                foundation_source=foundation_source,
            )

            exit_code, payload = self.run_fixture(source_root, tokens, report)

            self.assertEqual(1, exit_code)
            self.assertEqual([], payload["foundationFieldReferences"]["borders"]["focus"])
            focus_evidence = payload["foundationFieldEvidence"]["borders"]["focus"]
            self.assertEqual([], focus_evidence["resolverReferences"])
            self.assertEqual({}, focus_evidence["resolverConsumers"])

    def test_reachable_theme_projection_credits_aggregate_field(self) -> None:
        """A reachable compatibility projection may consume a field as `typography.input`."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            foundation_source = FOUNDATION_SOURCE.replace(
                "public data class PixelTypographyTokens(public val body: Int)",
                "public data class PixelTypographyTokens(public val body: Int, public val input: Int)",
            ) + """
public data class PixelThemeTokens(public val typography: PixelTypographyTokens) {
  public fun projectInput(): Int = typography.input
}
"""
            runtime_source = COMPLETE_RUNTIME_SOURCE.replace(
                "  consume(theme.typography.body)\n",
                "  consume(theme.typography.body)\n  consume(theme.projectInput())\n",
            )
            source_root, tokens, report = self.write_fixture(
                root,
                runtime_source,
                foundation_source=foundation_source,
            )

            exit_code, payload = self.run_fixture(source_root, tokens, report)

            self.assertEqual(0, exit_code)
            input_evidence = payload["foundationFieldEvidence"]["typography"]["input"]
            self.assertTrue(input_evidence["resolverReferences"])
            self.assertIn("projectInput", input_evidence["resolverConsumers"])

    def test_disconnected_component_binding_fails_color_geometry_and_focus(self) -> None:
        """Merely assigning `theme.components.button` cannot masquerade as a rendered consumer."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            runtime_source = COMPLETE_RUNTIME_SOURCE.replace(
                "  consume(buttonTokens.resolveContainerColor(states, theme.colors))\n"
                "  consume(buttonTokens.resolveContentColor(states, theme.colors))\n"
                "  consume(buttonTokens.resolvePadding(theme.spacing))\n"
                "  consume(withControlFocusIndicator(componentTokens = buttonTokens))\n",
                "  consume(buttonTokens)\n",
            )
            source_root, tokens, report = self.write_fixture(root, runtime_source)

            exit_code, payload = self.run_fixture(source_root, tokens, report)

            button_reasons = {
                finding["reason"]
                for finding in payload["findings"]
                if finding["token"] == "button"
            }
            self.assertEqual(1, exit_code)
            self.assertIn("NO_RUNTIME_CONSUMER", button_reasons)
            self.assertIn("INSUFFICIENT_COLOR_FIELD_CONSUMERS", button_reasons)
            self.assertIn("NO_GEOMETRY_FIELD_CONSUMER", button_reasons)
            self.assertIn("NO_FOCUS_FIELD_CONSUMER", button_reasons)
            self.assertTrue(payload["componentBindings"]["button"])
            self.assertEqual([], payload["componentReferences"]["button"])

    def test_default_token_identity_branch_fails_even_with_complete_consumption(self) -> None:
        """Behavior branching on default-token object equality is reported as an identity cliff."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            runtime_source = COMPLETE_RUNTIME_SOURCE.replace(
                "  val buttonTokens = PixelTheme.of(context).components.button\n",
                "  val buttonTokens = PixelTheme.of(context).components.button\n"
                "  consume(buttonTokens == PixelComponentTokens.Default.button)\n",
            )
            source_root, tokens, report = self.write_fixture(root, runtime_source)

            exit_code, payload = self.run_fixture(source_root, tokens, report)

            button_reasons = {
                finding["reason"]
                for finding in payload["findings"]
                if finding["token"] == "button"
            }
            self.assertEqual(1, exit_code)
            self.assertIn("TOKEN_IDENTITY_BRANCH", button_reasons)

    def test_focusable_family_cannot_disable_its_default_focus_token(self) -> None:
        """A focusable family with `focusIndicator = null` fails despite calling the focus helper."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            component_source = COMPONENT_SOURCE.replace(
                "public val button: PixelComponentColorTokens = PixelComponentColorTokens()",
                "public val button: PixelComponentColorTokens = "
                "PixelComponentColorTokens(focusIndicator = null)",
            )
            source_root, tokens, report = self.write_fixture(
                root,
                COMPLETE_RUNTIME_SOURCE,
                component_source=component_source,
            )

            exit_code, payload = self.run_fixture(source_root, tokens, report)

            button_reasons = {
                finding["reason"]
                for finding in payload["findings"]
                if finding["token"] == "button"
            }
            self.assertEqual(1, exit_code)
            self.assertIn("FOCUS_TOKEN_DISABLED", button_reasons)

    def test_new_component_family_requires_focus_unless_explicitly_passive(self) -> None:
        """A future component cannot silently evade focus coverage by missing a hard-coded allowlist."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            component_source = COMPONENT_SOURCE.replace("toast", "chip")
            runtime_source = COMPLETE_RUNTIME_SOURCE.replace("toast", "chip")
            source_root, tokens, report = self.write_fixture(
                root,
                runtime_source,
                component_source=component_source,
            )

            exit_code, payload = self.run_fixture(source_root, tokens, report)

            chip_reasons = {
                finding["reason"]
                for finding in payload["findings"]
                if finding["token"] == "chip"
            }
            self.assertEqual(1, exit_code)
            self.assertIn("NO_FOCUS_FIELD_CONSUMER", chip_reasons)

    def test_missing_definition_writes_failure_evidence(self) -> None:
        """A missing canonical token file returns nonzero with a stable reason code."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            source_root = root / "main"
            source_root.mkdir()
            (source_root / "Runtime.kt").write_text("fun build() = Unit\n", encoding="utf-8")
            report = root / "coverage.json"

            exit_code, payload = self.run_fixture(
                source_root,
                source_root / "Missing.kt",
                report,
            )

            self.assertEqual(1, exit_code)
            self.assertEqual("TOKEN_DEFINITION_NOT_FOUND", payload["findings"][0]["reason"])


if __name__ == "__main__":
    unittest.main()
