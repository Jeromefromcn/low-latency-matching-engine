# ADR 0001 — Separate the measurement plane from the monitoring plane

- Status: Accepted
- Date: 2026-10-22

## Context

A Prometheus + Grafana stack already exists and is the obvious tool for "showing latency". The engine targets single-digit to tens of microseconds per message, and the numbers that matter are the tail (p99.9, p99.99, max).

Problems with using Prometheus as the latency source:

1. **Resolution.** Default scrape interval is 15 s; histogram buckets are pre-defined and coarse. Aggregating over scrape windows and buckets smooths away exactly the tail outliers we need to see.
2. **Observer effect.** Calling a metrics client on the hot path allocates and/or contends (shared atomic counters, label lookups), adding the very jitter we are trying to measure.
3. **Evidence.** A Grafana panel is not reproducible raw data; it cannot be re-analysed or audited.

## Decision

- **Measurement plane (evidence):** hot-path threads stamp `System.nanoTime()` at stage boundaries and record into a per-thread HdrHistogram (single writer, pre-allocated, no allocation per record). A housekeeping thread pinned to CPU 0 snapshots interval histograms and writes `.hlog` interval logs and `.hgrm` distributions to disk. These files are the only accepted evidence for latency claims.
- **Monitoring plane (operations):** the same housekeeping thread derives summary values (selected percentiles, throughput, GC counts) from the snapshots and exposes them to Prometheus. Grafana is used for trends and operational visibility only.
- An automated architecture test ensures no metrics-library class is reachable from hot-path code.

## Consequences

- Latency claims are reproducible and auditable from committed raw files.
- Grafana dashboards remain useful for spotting regressions during long runs but are explicitly not cited as proof.
- Slight extra complexity: the housekeeping thread owns both log writing and metric export.

## Alternatives considered

- **Prometheus native histograms / high-resolution buckets:** better resolution but still scrape-window aggregation and still a client call on the hot path.
- **Micrometer timers:** same observer-effect concern; designed for service-level latencies in milliseconds.
