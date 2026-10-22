# Benchmark Methodology

Every latency number published from this repository follows these rules. If a run does not follow them, its results may be kept for exploration but must not be quoted.

Methodology version: **1** (bump and log changes at the bottom when any rule changes).

## 1. What is measured

| Name | From | To |
|---|---|---|
| **Engine latency** | message read from inbound ring buffer | last resulting event written to outbound ring buffer |
| **Pipeline latency** | intended send time in load generator | response observed by load generator |
| **Stage latencies** | ingress → decode → risk → match → encode → egress | per-stage `System.nanoTime()` deltas |

All on one machine (same clock), so `System.nanoTime()` deltas are valid. Cross-machine latency (later phases) must state how clocks were handled.

## 2. Load model

- **Open model, constant rate.** The load generator schedules each message at `start + i / rate`. Latency is measured from the **intended** send time, not the actual send time. This corrects for coordinated omission: if the generator itself stalls, the stall is charged to latency instead of hidden.
- Never use a closed loop (send → wait for reply → send) for latency numbers.
- State the rate (msgs/s) for every result. Report at least two rates: a comfortable one and one close to saturation.

## 3. Warm-up and duration

- Warm-up: at least **60 s** at target rate (or until JIT compilation log is quiet) — warm-up samples are discarded.
- Measurement window: at least **10 minutes** for headline numbers (so p99.99 has ≥ several thousand samples above it at typical rates).
- State the sample count.

## 4. What to report

Always: **p50, p90, p99, p99.9, p99.99, max**, sample count, rate, duration.

Never: averages alone, or a single percentile without max.

Plots: percentile distribution (x-axis log-scaled "1/(1-percentile)"), as produced from `.hgrm` files.

## 5. Recording

- Histograms: HdrHistogram, 3 significant digits, highest trackable value ≥ 10 s.
- Hot-path threads record into their own histogram (single writer). The housekeeping thread snapshots interval histograms via `Recorder`/`SingleWriterRecorder` and writes `.hlog` interval logs.
- Prometheus/Grafana only show summarised values derived from the same histograms. They are **not** evidence (see ADR 0001).

## 6. Environment control

Headline results must come from the **tuned benchmark machine** (see `CLAUDE.md` → Environment):

- Turbo disabled, frequency locked at base clock, deep C-states limited.
- SMT disabled (`nosmt`), CPUs 1–3 isolated, IRQs on CPU 0.
- Threads pinned exactly as in the core-allocation table.
- No other workload running. Machine on AC power, thermals verified (Phase 1.1).
- Run `scripts/platform/collect-env.sh` immediately before each run.

Results from VMs, laptops-in-default-state, cloud hosts or CI runners are labelled `exploratory` in the directory name and in any text that cites them.

## 7. Result directory layout

```
bench/<area>/<label>-<YYYYmmdd-HHMMSS>/
├── command.txt          exact command line(s) and git commit hash
├── env/                 output of collect-env.sh
├── gc.log               JVM unified GC log (-Xlog:gc*)
├── *.hlog               HdrHistogram interval logs
├── *.hgrm               percentile distribution(s)
└── notes.md             what was varied, observations
```

Raw files are committed. Never edit them; re-run instead.

## 8. Comparisons

A before/after comparison changes **one variable** only, uses identical rate/duration/warm-up, and runs back-to-back on the same boot where possible. Run each configuration at least **3 times**; report the spread, not just the best run.

## 9. Change log

| Version | Date | Change |
|---|---|---|
| 1 | 2026-10-22 | Initial methodology |
