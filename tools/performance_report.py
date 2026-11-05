#!/usr/bin/env python3
"""Summarize Macrobenchmark results without treating emulator timing as device FPS."""

import argparse
import json
import math
import re
import subprocess
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


def summarize_traces(directory, processor, output):
    query = """
    SELECT t.name AS thread, s.name AS section, COUNT(*) AS count,
           ROUND(SUM(s.dur)/1e6,3) AS total_ms, ROUND(MAX(s.dur)/1e6,3) AS max_ms
    FROM slice s JOIN thread_track tt ON s.track_id=tt.id
    JOIN thread t ON tt.utid=t.utid JOIN process p ON t.upid=p.upid
    WHERE p.name GLOB 'moe.hycnyl.ehviewer*' AND s.dur>0 AND
      (LOWER(s.name) LIKE '%compos%' OR s.name LIKE '%com.hippo.ehviewer%' OR
       s.name LIKE '%com.ehviewer.core%' OR LOWER(s.name) LIKE '%decode%' OR
       LOWER(s.name) LIKE '%bitmap%' OR LOWER(s.name) LIKE '%upload%' OR
       LOWER(s.name) LIKE '%blur%' OR s.name GLOB 'Choreographer*' OR
       s.name GLOB '*DrawFrame*' OR s.name GLOB '*traversal*')
    GROUP BY t.utid,s.name ORDER BY max_ms DESC LIMIT 60;
    SELECT t.name AS thread, state, COUNT(*) AS count, ROUND(SUM(dur)/1e6,3) AS total_ms,
           ROUND(MAX(dur)/1e6,3) AS max_ms
    FROM thread_state JOIN thread t USING(utid) JOIN process p USING(upid)
    WHERE p.name GLOB 'moe.hycnyl.ehviewer*' AND t.is_main_thread=1 AND dur>0
    GROUP BY t.utid,state ORDER BY total_ms DESC;
    """
    selected = {}
    for trace in sorted(directory.rglob("*.perfetto-trace")):
        selected.setdefault(re.sub(r"_iter\d+.*", "", trace.name), trace)
    if not selected:
        raise RuntimeError("No Perfetto traces accompanied the measurements")
    output.mkdir(parents=True, exist_ok=True)
    for trace in selected.values():
        result = subprocess.run([str(processor), "query", "--quiet", str(trace), query],
                                text=True, capture_output=True, check=True)
        (output / (trace.stem + ".txt")).write_text(result.stdout)
    return len(selected)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--trace-processor", type=Path)
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
    if args.trace_processor:
        count = summarize_traces(args.results, args.trace_processor, args.output.parent / "perfetto-sections")
        lines += [f"Perfetto thread/Compose/decode/bitmap/render sections were queried for {count} scenario traces. Slice durations include nested work and waiting; inspect the complete trace before attributing a stall.", ""]
    args.output.write_text("\n".join(lines))
    print(args.output.read_text())


if __name__ == "__main__":
    main()
