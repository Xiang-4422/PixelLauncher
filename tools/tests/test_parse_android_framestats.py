from __future__ import annotations

import unittest

from tools import parse_android_framestats


class AndroidFrameStatsParserTest(unittest.TestCase):
    """Verifies deterministic frame latency and missed-deadline calculations for M0 evidence."""

    def test_parse_framestats_calculates_percentiles_and_deadlines(self) -> None:
        """Valid frames use FrameCompleted minus IntendedVsync and the platform deadline column."""

        raw = "\n".join(
            [
                "Graphics info for pid 42 [sample]",
                "---PROFILEDATA---",
                "Flags,FrameTimelineVsyncId,IntendedVsync,Vsync,FrameDeadline,FrameCompleted",
                "0,1,1000000,1000000,18000000,11000000",
                "0,2,20000000,20000000,36000000,40000000",
                "1,3,50000000,50000000,66000000,70000000",
                "---PROFILEDATA---",
            ],
        )

        parsed = parse_android_framestats.parse_framestats(raw, refresh_rate=60.0)

        self.assertEqual(2, parsed["frameCount"])
        self.assertEqual(10.0, parsed["p50Milliseconds"])
        self.assertEqual(20.0, parsed["p95Milliseconds"])
        self.assertEqual(1, parsed["missedDeadlineFrames"])

    def test_parse_framestats_rejects_empty_profile(self) -> None:
        """A launch with no valid frame rows is missing evidence rather than a zero-cost success."""

        with self.assertRaisesRegex(ValueError, "No valid frames"):
            parse_android_framestats.parse_framestats("---PROFILEDATA---", refresh_rate=60.0)


if __name__ == "__main__":
    unittest.main()
