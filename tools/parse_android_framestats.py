#!/usr/bin/env python3
"""Convert Android gfxinfo framestats into a bounded machine-readable M0 device baseline."""

from __future__ import annotations

import argparse
import csv
import io
import json
import math
import sys
from pathlib import Path
from typing import Sequence


def parse_arguments(arguments: Sequence[str]) -> argparse.Namespace:
    """Parse required device identity and refresh-rate metadata supplied by the adb wrapper."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True, help="Raw dumpsys gfxinfo framestats output.")
    parser.add_argument("--output", type=Path, required=True, help="JSON summary output.")
    parser.add_argument("--package", required=True, help="Measured Android package.")
    parser.add_argument("--model", required=True, help="Android device model reported by getprop.")
    parser.add_argument("--api-level", type=int, required=True, help="Android SDK level.")
    parser.add_argument("--refresh-rate", type=float, required=True, help="Active display refresh rate in Hz.")
    return parser.parse_args(arguments)


def percentile(sorted_values: Sequence[float], percentile_value: float) -> float:
    """Return the nearest-rank percentile, which stays deterministic for small frame samples."""

    if not sorted_values:
        raise ValueError("Cannot calculate a percentile for an empty frame sample.")
    rank = max(1, math.ceil(percentile_value * len(sorted_values)))
    return sorted_values[min(rank - 1, len(sorted_values) - 1)]


def parse_framestats(text: str, refresh_rate: float) -> dict[str, object]:
    """Parse valid gfxinfo rows and calculate total frame latency plus deadline misses."""

    if refresh_rate <= 0:
        raise ValueError("Display refresh rate must be positive.")
    frame_budget_nanos = 1_000_000_000.0 / refresh_rate
    frame_durations_ms: list[float] = []
    missed_deadlines = 0
    lines = text.splitlines()
    for index, line in enumerate(lines):
        # Android releases add columns (for example FrameTimelineVsyncId) without preserving position.
        header_fields = line.split(",")
        if not line.startswith("Flags,") or not {
            "Flags",
            "IntendedVsync",
            "FrameCompleted",
        }.issubset(header_fields):
            continue
        reader = csv.DictReader(io.StringIO("\n".join(lines[index:])))
        for row in reader:
            if not row or row.get("Flags") is None:
                break
            try:
                flags = int(row["Flags"])
                intended_vsync = int(row["IntendedVsync"])
                frame_completed = int(row["FrameCompleted"])
            except (KeyError, TypeError, ValueError):
                break
            if flags != 0 or frame_completed <= intended_vsync:
                continue
            duration_nanos = frame_completed - intended_vsync
            frame_durations_ms.append(duration_nanos / 1_000_000.0)
            frame_deadline_text = row.get("FrameDeadline")
            if frame_deadline_text and frame_deadline_text.isdigit() and int(frame_deadline_text) > 0:
                missed_deadlines += int(frame_completed > int(frame_deadline_text))
            else:
                missed_deadlines += int(duration_nanos > frame_budget_nanos)
        break

    if not frame_durations_ms:
        raise ValueError("No valid frames were found in gfxinfo framestats output.")
    sorted_durations = sorted(frame_durations_ms)
    return {
        "frameCount": len(sorted_durations),
        "frameBudgetMilliseconds": frame_budget_nanos / 1_000_000.0,
        "p50Milliseconds": percentile(sorted_durations, 0.50),
        "p95Milliseconds": percentile(sorted_durations, 0.95),
        "p99Milliseconds": percentile(sorted_durations, 0.99),
        "maxMilliseconds": sorted_durations[-1],
        "missedDeadlineFrames": missed_deadlines,
        "missedDeadlinePercent": missed_deadlines * 100.0 / len(sorted_durations),
    }


def main(arguments: Sequence[str] | None = None) -> int:
    """Write one device baseline summary or fail when raw frame evidence is unusable."""

    options = parse_arguments(arguments if arguments is not None else sys.argv[1:])
    try:
        metrics = parse_framestats(
            options.input.read_text(encoding="utf-8", errors="replace"),
            options.refresh_rate,
        )
        report = {
            "status": "measured",
            "kind": "gfxinfo-framestats",
            "package": options.package,
            "device": {
                "model": options.model,
                "apiLevel": options.api_level,
                "refreshRateHz": options.refresh_rate,
            },
            "metrics": metrics,
            "limitations": [
                "Launch-only gfxinfo evidence; this is not a Macrobenchmark or a release regression gate.",
                "PixelHost build/layout/paint/submit stages are not separated in this M0 baseline.",
            ],
        }
        options.output.parent.mkdir(parents=True, exist_ok=True)
        options.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    except Exception as error:
        print(f"Android frame baseline parsing failed: {error}", file=sys.stderr)
        return 1
    print(f"Android frame baseline written to {options.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
