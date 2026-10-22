#!/usr/bin/env python3
"""Compare two platform baselines (e.g. A-untuned vs B-tuned).

Usage:
    python3 plot_baseline.py <dir-A> <dir-B> [--out <dir>]

Reads from each run directory (produced by run-baseline.sh):
    cyclictest.txt   histogram in microseconds (-h1000 -q output)
    jhiccup.hgrm     HdrHistogram percentile distribution, values in milliseconds
    hwlatdetect.txt  summary lines

Writes to --out (default: the parent of dir-B):
    comparison.md    percentile table (microseconds)
    comparison.png   percentile distribution plots

Requires: matplotlib  (pip install matplotlib)
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

PERCENTILES = [50.0, 90.0, 99.0, 99.9, 99.99]


# ---------------------------------------------------------------- parsing
def parse_cyclictest(path: Path):
    """Return (buckets, counts, overflow, max_us) or None if file missing."""
    if not path.exists():
        return None
    buckets, counts = [], []
    overflow, max_us = 0, None
    for line in path.read_text().splitlines():
        line = line.strip()
        if re.match(r"^\d+\s+\d+", line):
            parts = line.split()
            buckets.append(int(parts[0]))
            counts.append(sum(int(x) for x in parts[1:]))
        elif line.startswith("# Histogram Overflows:"):
            overflow = sum(int(x) for x in line.split(":")[1].split())
        elif line.startswith("# Max Latencies:"):
            max_us = max(int(x) for x in line.split(":")[1].split())
    if not buckets:
        return None
    return buckets, counts, overflow, max_us


def cyclictest_percentiles(data):
    buckets, counts, overflow, max_us = data
    total = sum(counts) + overflow
    result = {}
    for p in PERCENTILES:
        target = total * p / 100.0
        running, value = 0, None
        for b, c in zip(buckets, counts):
            running += c
            if running >= target:
                value = float(b)
                break
        result[p] = value if value is not None else float("inf")  # beyond histogram range
    result["max"] = float(max_us) if max_us is not None else float("nan")
    result["samples"] = total
    return result


def cyclictest_curve(data):
    """Points (1/(1-p), latency_us) for plotting."""
    buckets, counts, overflow, _ = data
    total = sum(counts) + overflow
    xs, ys, running = [], [], 0
    for b, c in zip(buckets, counts):
        if c == 0:
            continue
        running += c
        frac = running / total
        if frac >= 1.0:
            break
        xs.append(1.0 / (1.0 - frac))
        ys.append(float(b))
    return xs, ys


def parse_hgrm(path: Path, scale_to_us: float = 1000.0):
    """Return list of (value_us, percentile_fraction, total_count) or None."""
    if not path.exists():
        return None
    rows = []
    for line in path.read_text().splitlines():
        parts = line.split()
        if len(parts) >= 3 and re.match(r"^[\d.]+$", parts[0]) and re.match(r"^[\d.]+$", parts[1]):
            rows.append((float(parts[0]) * scale_to_us, float(parts[1]), int(float(parts[2]))))
    return rows or None


def hgrm_percentiles(rows):
    result = {}
    for p in PERCENTILES:
        frac = p / 100.0
        value = next((v for v, q, _ in rows if q >= frac - 1e-12), rows[-1][0])  # tolerate float error
        result[p] = value
    result["max"] = rows[-1][0]
    result["samples"] = rows[-1][2]
    return result


def hgrm_curve(rows):
    xs, ys = [], []
    for v, q, _ in rows:
        if q < 1.0:
            xs.append(1.0 / (1.0 - q))
            ys.append(v)
    return xs, ys


def parse_hwlat(path: Path):
    if not path.exists():
        return "not run"
    keep = [l.strip() for l in path.read_text().splitlines()
            if re.search(r"(Max Latency|Samples exceeding|Samples recorded|Below threshold)", l)]
    return "; ".join(keep) if keep else "see hwlatdetect.txt"


# ---------------------------------------------------------------- output
def fmt(v):
    if v is None:
        return "n/a"
    if v == float("inf"):
        return ">999"
    if v != v:  # NaN
        return "n/a"
    return f"{v:,.1f}" if v < 100 else f"{v:,.0f}"


def table(title, a, b, label_a, label_b):
    head = ["Run"] + [f"p{p:g}" for p in PERCENTILES] + ["max", "samples"]
    lines = [f"### {title} (µs)", "", "| " + " | ".join(head) + " |",
             "|" + "---|" * len(head)]
    for label, r in ((label_a, a), (label_b, b)):
        if r is None:
            lines.append(f"| {label} | " + " | ".join(["n/a"] * (len(head) - 1)) + " |")
            continue
        cells = [fmt(r[p]) for p in PERCENTILES] + [fmt(r["max"]), f"{r['samples']:,}"]
        lines.append(f"| {label} | " + " | ".join(cells) + " |")
    return "\n".join(lines) + "\n"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("dir_a", type=Path)
    ap.add_argument("dir_b", type=Path)
    ap.add_argument("--out", type=Path)
    args = ap.parse_args()
    out = args.out or args.dir_b.parent
    out.mkdir(parents=True, exist_ok=True)
    label_a, label_b = args.dir_a.name, args.dir_b.name

    ct = {k: parse_cyclictest(d / "cyclictest.txt") for k, d in (("a", args.dir_a), ("b", args.dir_b))}
    jh = {k: parse_hgrm(d / "jhiccup.hgrm") for k, d in (("a", args.dir_a), ("b", args.dir_b))}

    md = [f"# Platform baseline: {label_a} vs {label_b}", ""]
    md.append(table("cyclictest — OS scheduling latency",
                    ct["a"] and cyclictest_percentiles(ct["a"]),
                    ct["b"] and cyclictest_percentiles(ct["b"]), label_a, label_b))
    md.append(table("jHiccup — idle JVM stalls",
                    jh["a"] and hgrm_percentiles(jh["a"]),
                    jh["b"] and hgrm_percentiles(jh["b"]), label_a, label_b))
    md += ["### hwlatdetect — firmware/SMI latency", "",
           f"- **{label_a}**: {parse_hwlat(args.dir_a / 'hwlatdetect.txt')}",
           f"- **{label_b}**: {parse_hwlat(args.dir_b / 'hwlatdetect.txt')}", "",
           "![comparison](comparison.png)", ""]
    (out / "comparison.md").write_text("\n".join(md))

    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
        from matplotlib.ticker import FixedLocator, FixedFormatter
    except ImportError:
        print("matplotlib not installed — wrote comparison.md only")
        return

    fig, axes = plt.subplots(1, 2, figsize=(12, 4.5))
    ticks = [1, 10, 100, 1_000, 10_000, 100_000]
    tick_labels = ["0%", "90%", "99%", "99.9%", "99.99%", "99.999%"]
    for ax, title, series in (
        (axes[0], "cyclictest (OS scheduling latency)",
         [(label_a, ct["a"] and cyclictest_curve(ct["a"])), (label_b, ct["b"] and cyclictest_curve(ct["b"]))]),
        (axes[1], "jHiccup (idle JVM stalls)",
         [(label_a, jh["a"] and hgrm_curve(jh["a"])), (label_b, jh["b"] and hgrm_curve(jh["b"]))]),
    ):
        for (label, curve), style in zip(series, ("-", "--")):
            if curve and curve[0]:
                ax.plot(curve[0], curve[1], style, label=label, linewidth=1.8)
        ax.set_xscale("log")
        ax.set_yscale("log")
        ax.xaxis.set_major_locator(FixedLocator(ticks))
        ax.xaxis.set_major_formatter(FixedFormatter(tick_labels))
        ax.set_xlabel("Percentile")
        ax.set_ylabel("Latency (µs)")
        ax.set_title(title)
        ax.grid(True, which="both", alpha=0.3)
        ax.legend()
    fig.tight_layout()
    fig.savefig(out / "comparison.png", dpi=150)
    print(f"Wrote {out / 'comparison.md'} and {out / 'comparison.png'}")


if __name__ == "__main__":
    main()
