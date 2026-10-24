# Low-Latency Matching Engine (Java)

[![build](https://github.com/Jeromefromcn/low-latency-matching-engine/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/Jeromefromcn/low-latency-matching-engine/actions/workflows/build.yml)

A limit-order-book matching engine that explores how far tail latency can be pushed on the JVM — with every number backed by raw, reproducible measurement data.

> **Status:** Phase 0 complete — build, CI, formatting and an end-to-end walking skeleton (no matching logic yet). Next: Phase 2, the matching engine. See [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Goals

- **Deterministic tail latency.** Target microsecond-level p99.9/p99.99 and max, not averages.
- **Zero allocation on the hot path**, proven by JMH `-prof gc` and GC-free steady-state runs.
- **Provable correctness.** Property-based tests, a reference-model differential test, and byte-identical deterministic replay.
- **Honest measurement.** Constant-rate (open-model) load to avoid coordinated omission; HdrHistogram logs committed for every published number.

## Design at a glance

```mermaid
flowchart LR
    loadgen["loadgen (CPU 1)"] --> inbound[inbound ring buffer]
    inbound --> engine["matching engine (CPU 2)"]
    engine --> outbound[outbound ring buffer]
    engine --- journal["journal + Aeron driver (CPU 3)"]

    housekeeping["housekeeping (CPU 0):<br/>histogram snapshots"] --> hlog[".hlog files (evidence)"]
    housekeeping --> prom["Prometheus summaries (monitoring only)"]
```

The diagram is the target design. **What exists today** — modules, threads, message flow, wire format, latency measurement, configuration, build and run modes — is described in [`docs/architecture.md`](docs/architecture.md), which is kept current with the code.

| Module | Responsibility |
|---|---|
| [`common/`](common/) | Wire format and constants shared by all modules |
| [`engine-core/`](engine-core/) | Order book and matching: pure, deterministic, no I/O (Phase 2) |
| [`engine-app/`](engine-app/) | Process wiring: ring buffers, threads, histograms, `.hlog` output |
| [`loadgen/`](loadgen/) | Constant-rate load generator; latency from intended send time |
| [`bench-jmh/`](bench-jmh/) | JMH micro-benchmarks |
| [`build-logic/`](build-logic/) | Gradle conventions shared by every module |

Key decisions are recorded as ADRs:

- [0001 — Separate the measurement plane from the monitoring plane](docs/adr/0001-measurement-vs-monitoring-plane.md)
- [0002 — In-process ring buffers / Aeron instead of Kafka on the message path](docs/adr/0002-aeron-ipc-over-kafka.md)
- [0003 — Gradle first, Maven parity later](docs/adr/0003-build-tool-gradle-first.md)
- [0004 — Hot-path processes run on bare metal, not in containers](docs/adr/0004-deployment-bare-metal-hot-path.md)
- [0005 — Engine message schema (SBE)](docs/adr/0005-sbe-message-schema.md)

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

Requires a JDK 25 for the Gradle toolchain; the wrapper downloads Gradle itself.

## Running

```
scripts/run-skeleton.sh                              # day-to-day: dev profile, 10 s warm-up + 60 s
scripts/run-skeleton.sh rate=20000 durationSeconds=30
EVIDENCE=1 scripts/run-skeleton.sh                   # evidence run, to be committed under bench/
```

A **day-to-day run** writes to `build/runs/skeleton/` (git-ignored) and replaces the previous run, so nothing accumulates. An **evidence run** is generated deliberately when a result is going to be published: it requires a clean working tree and writes a new `bench/skeleton/<label>-<timestamp>/` with the full layout from the [methodology](docs/benchmark-methodology.md) §7, including an environment snapshot.
