#!/usr/bin/env python3
"""Compare matching benchmark summary directories; exit nonzero for missing data or regressions."""
import argparse
import csv
import math
from pathlib import Path


def compare(baseline, current, tolerance):
    failures = []
    files = sorted(baseline.glob("*-summary.csv"))
    if not files:
        raise ValueError("Baseline contains no summary CSV files")
    for path in files:
        candidate = current / path.name
        if not candidate.exists():
            failures.append(f"Missing scenario: {path.name}")
            continue
        with path.open(encoding="utf-8") as stream:
            before = {row["metric"]: row for row in csv.DictReader(stream)}
        with candidate.open(encoding="utf-8") as stream:
            after = {row["metric"]: row for row in csv.DictReader(stream)}
        if not before or not after:
            raise ValueError(f"Empty scenario: {path.name}")
        for name, old in before.items():
            new = after.get(name)
            if new is None or old["unit"] != new["unit"]:
                failures.append(f"Missing metric or changed unit: {path.name}/{name}")
                continue
            # Node counts and long-frame percentages remain diagnostic; noisy zero baselines
            # must not turn into misleading relative gates. Latency: p95; throughput: mean.
            unit = old["unit"]
            if unit not in ("ms", "Hz"):
                continue
            column = "mean" if unit == "Hz" else "p95"
            previous, measured = float(old[column]), float(new[column])
            if not math.isfinite(previous) or not math.isfinite(measured) or previous <= 0 or measured <= 0:
                raise ValueError(f"Invalid timing: {path.name}/{name}")
            regressed = measured < previous * (1 - tolerance) if unit == "Hz" else measured > previous * (1 + tolerance)
            if regressed:
                failures.append(f"{path.name}/{name} {column}: {previous:.3f} -> {measured:.3f} {unit}")
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("current", type=Path)
    parser.add_argument("--tolerance", type=float, default=.20, help="Relative tolerance, default 0.20")
    args = parser.parse_args()
    if not 0 <= args.tolerance < 1:
        parser.error("tolerance must be in [0, 1)")
    try:
        failures = compare(args.baseline, args.current, args.tolerance)
    except (ValueError, OSError, KeyError) as error:
        parser.exit(2, f"Invalid performance report: {error}\n")
    for failure in failures:
        print(failure)
    if not failures:
        print("No timing regressions in matching baseline scenarios.")
    return bool(failures)


if __name__ == "__main__":
    raise SystemExit(main())
