from __future__ import annotations

import tempfile
import unittest
import zipfile
from pathlib import Path

from tools import verify_baseline_profile


# Minimal valid engine profile used by isolated archive-verification tests.
ENGINE_PROFILE = (
    "Lcom/purride/pixelcore/PixelBuffer;\n"
    "SPLcom/purride/pixelcore/PixelBuffer;->clear()V\n"
    "Lcom/purride/pixelui/PixelHostView;\n"
)


class BaselineProfileVerifierTest(unittest.TestCase):
    """Locks source ownership and final AAR/APK packaging failure semantics."""

    def test_engine_aar_must_contain_exact_reviewed_profile(self) -> None:
        """A stale AAR cannot pass merely because it has a same-named profile entry."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # The temporary root keeps synthetic archives isolated from production build outputs.
            root = Path(temporary_directory)
            # The AAR intentionally carries different bytes under the correct entry name.
            aar = root / "engine.aar"
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr(verify_baseline_profile.AAR_PROFILE_ENTRY, "Lstale/Profile;\n")

            with self.assertRaisesRegex(ValueError, "stale or altered"):
                verify_baseline_profile.inspect_engine_aar(aar, root, ENGINE_PROFILE.encode("utf-8"))

    def test_consumer_apk_requires_both_binary_profiles_and_installer(self) -> None:
        """A profile without metadata or ProfileInstaller is not a deployable consumer package."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # The temporary root keeps the deliberately incomplete APK out of build outputs.
            root = Path(temporary_directory)
            # Only the primary binary is written; metadata and installer marker remain absent.
            apk = root / "consumer.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr(verify_baseline_profile.APK_PROFILE_ENTRIES[0], b"profile")

            with self.assertRaisesRegex(ValueError, "baseline.profm"):
                verify_baseline_profile.inspect_consumer_apk(apk, root)

    def test_text_profile_rejects_referenced_but_foreign_owner(self) -> None:
        """A target-owned method mentioning PixelBuffer must not leak into the SDK profile."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # The temporary profile models the ownership bug that a broad substring filter would miss.
            root = Path(temporary_directory)
            # Its method returns an engine type but is owned by the benchmark target.
            profile = root / "baseline-prof.txt"
            profile.write_text(
                ENGINE_PROFILE
                + "PLcom/purride/pixelbenchmark/target/Route;->build()Lcom/purride/pixelui/Widget;\n",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ValueError, "Out-of-scope"):
                verify_baseline_profile.inspect_text_profile(
                    profile,
                    root,
                    ("Lcom/purride/pixelcore/", "Lcom/purride/pixelui/"),
                    ("Lcom/purride/pixelcore/PixelBuffer;", "Lcom/purride/pixelui/PixelHostView;"),
                    minimum_rule_count=1,
                )

    def test_complete_consumer_apk_records_non_empty_entries(self) -> None:
        """Successful evidence reports exact sizes for both profiles and ProfileInstaller."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # The fixture includes every entry expected from a release-like consumer build.
            root = Path(temporary_directory)
            # The APK name is retained in repository-relative evidence.
            apk = root / "consumer.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr(verify_baseline_profile.APK_PROFILE_ENTRIES[0], b"prof")
                archive.writestr(verify_baseline_profile.APK_PROFILE_ENTRIES[1], b"meta")
                archive.writestr(verify_baseline_profile.PROFILE_INSTALLER_MARKER, b"1.4.1")

            # The returned evidence is the stable input to the JSON report writer.
            evidence = verify_baseline_profile.inspect_consumer_apk(apk, root)

            self.assertEqual(4, evidence.profile_entries[verify_baseline_profile.APK_PROFILE_ENTRIES[0]])
            self.assertEqual(4, evidence.profile_entries[verify_baseline_profile.APK_PROFILE_ENTRIES[1]])
            self.assertEqual(5, evidence.profile_entries[verify_baseline_profile.PROFILE_INSTALLER_MARKER])


if __name__ == "__main__":
    unittest.main()
