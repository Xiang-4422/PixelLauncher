from __future__ import annotations

import io
import json
import tempfile
import unittest
from contextlib import redirect_stderr
from pathlib import Path

from tools import check_stable_api_boundary


class StableApiBoundaryTest(unittest.TestCase):
    """Proves the gate passes clean APIs and fails leaked internal parents and parameters."""

    def test_clean_signature_passes(self) -> None:
        """A signature containing only stable packages must produce a zero-finding report."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # Temporary paths isolate the fixture and generated evidence from the repository worktree.
            root = Path(temporary_directory)
            # The clean fixture includes an `InternalState` name to prove matching is package-segment aware.
            signature = root / "clean.api"
            signature.write_text(
                "// Signature format: 4.0\n"
                "package com.purride.pixelui { public class InternalState; }\n",
                encoding="utf-8",
            )
            # The JSON report is parsed to validate the machine contract as well as the exit code.
            report = root / "report.json"

            exit_code = check_stable_api_boundary.main(
                ["--signature", str(signature), "--report", str(report)],
            )

            self.assertEqual(0, exit_code)
            self.assertEqual("passed", json.loads(report.read_text(encoding="utf-8"))["status"])

    def test_internal_parent_and_parameter_fail(self) -> None:
        """An internal superclass or method parameter must make the tooling gate return nonzero."""

        with tempfile.TemporaryDirectory() as temporary_directory:
            # Temporary paths keep the deliberately invalid signature out of production baselines.
            root = Path(temporary_directory)
            # The fixture covers both inheritance and callable-signature leak paths requested by M1.
            signature = root / "leaky.api"
            signature.write_text(
                "// Signature format: 4.0\n"
                "package com.purride.pixelui {\n"
                "  public class Leaky extends com.purride.pixelui.internal.RenderBase {\n"
                "    method public void accept(com.purride.pixelcore.spi.internal.Secret value);\n"
                "  }\n"
                "}\n",
                encoding="utf-8",
            )
            # Failure evidence must still be written so release automation can diagnose the boundary breach.
            report = root / "report.json"
            # Captured stderr prevents the expected failure message from polluting the unit-test output.
            stderr = io.StringIO()

            with redirect_stderr(stderr):
                exit_code = check_stable_api_boundary.main(
                    ["--signature", str(signature), "--report", str(report)],
                )

            payload = json.loads(report.read_text(encoding="utf-8"))
            self.assertEqual(1, exit_code)
            self.assertEqual("failed", payload["status"])
            self.assertEqual(2, payload["findingCount"])
            self.assertIn("internal API reference", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
