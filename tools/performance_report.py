#!/usr/bin/env python3
"""Summarize Macrobenchmark results without treating emulator timing as device FPS."""

import argparse
import json
import math
from pathlib import Path


def percentile(values, quantile):
    ordered = sorted(values)
    position = (len(ordered) - 1) * quantile
    lo, hi = math.floor(position), math.ceil(position)
    return ordered[lo] + (ordered[hi] - ordered[lo]) * (position - lo)


def measurements(directory):
    result = {}
    for path in directory.rglob("*-benchmarkData.json"):
        report = json.loads(path.read_text())
        for benchmark in report.get("benchmarks", []):
            name = benchmark["name"]
            groups = {**benchmark.get("metrics", {}), **benchmark.get("sampledMetrics", {})}
            for metric in ("frameDurationCpuMs", "frameOverrunMs", "timeToInitialDisplayMs", "timeToFullDisplayMs"):
                if metric not in groups:
                    continue
                data = groups[metric]
                runs = data.get("runs", [])
                values = [v for run in runs for v in (run if isinstance(run, list) else [run])]
                stats = []
                for q in (50, 90, 95, 99):
                    value = data.get(f"P{q}")
                    if value is None and values:
                        value = percentile(values, q / 100)
                    stats.append(value)
                result[(name, metric)] = stats
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    current = measurements(args.results)
    if not current:
        raise RuntimeError("No Macrobenchmark measurements were produced")
    baseline = measurements(args.baseline) if args.baseline else {}
    lines = [
        "# Core UI performance",
        "",
        "CI uses a software-rendered Android 15 emulator. These numbers compare the same scenarios under the same configuration; they are not phone FPS or hardware GPU measurements.",
        "CompilationMode.Full is used for frame scenarios; cold-start tests separately compare no profile and required baseline profile.",
        "",
        "| Scenario | Metric (ms) | P50 | P90 | P95 | P99 | Baseline P95 |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: |",
    ]
    number = lambda value: "—" if value is None else f"{value:.3f}"
    for key, values in sorted(current.items()):
        before = baseline.get(key, [None] * 4)[2]
        lines.append("| " + " | ".join([*key, *map(number, values), number(before)]) + " |")
    lines += ["", "Raw benchmarkData.json and per-iteration Perfetto traces are retained alongside this report.", ""]
    args.output.write_text("\n".join(lines))
    print(args.output.read_text())


if __name__ == "__main__":
    main()
