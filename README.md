# Low-Latency Matching Engine (Java)

A limit-order-book matching engine that explores how far tail latency can be pushed on the JVM — with every number backed by raw, reproducible measurement data.

> **Status:** Phase 0 — bootstrapping. See [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Goals

- **Deterministic tail latency.** Target microsecond-level p99.9/p99.99 and max, not averages.
- **Zero allocation on the hot path**, proven by JMH `-prof gc` and GC-free steady-state runs.
- **Provable correctness.** Property-based tests, a reference-model differential test, and byte-identical deterministic replay.
- **Honest measurement.** Constant-rate (open-model) load to avoid coordinated omission; HdrHistogram logs committed for every published number.

## Design at a glance

```
loadgen (CPU 1) ──► inbound ring buffer ──► matching engine (CPU 2) ──► outbound ring buffer
                                                   │
                                     journal + Aeron driver (CPU 3)
housekeeping (CPU 0): histogram snapshots → .hlog files (evidence)
                                          → Prometheus summaries (monitoring only)
```

Key decisions are recorded as ADRs:

- [0001 — Separate the measurement plane from the monitoring plane](docs/adr/0001-measurement-vs-monitoring-plane.md)
- [0002 — In-process ring buffers / Aeron instead of Kafka on the message path](docs/adr/0002-aeron-ipc-over-kafka.md)
- [0003 — Gradle first, Maven parity later](docs/adr/0003-build-tool-gradle-first.md)

## Results

Headline numbers will appear here once Phase 3 is complete, each linked to its raw data under [`bench/`](bench/).

Platform noise baseline (untuned vs tuned machine): [`bench/platform/`](bench/platform/) — Phase 1.

## Benchmark machine

Intel Core i5-10300H laptop, 4 cores (SMT disabled), turbo off, frequency locked at 2.5 GHz, CPUs 1–3 isolated, Ubuntu Server 24.04, Java 25. Full procedure: [`docs/runbooks/01-platform-setup.md`](docs/runbooks/01-platform-setup.md). Methodology: [`docs/benchmark-methodology.md`](docs/benchmark-methodology.md).

Consumer hardware is a deliberate constraint: the point is the method and the relative gains, measured honestly, including the noise floor the hardware cannot remove.

## Building

```
./gradlew build
```

(Available from Phase 0.)
