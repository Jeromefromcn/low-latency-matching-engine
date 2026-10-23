# CLAUDE.md

Guidance for Claude Code when working in this repository. Read this file fully before every task.

## What this project is

A Java limit-order-book matching engine built to demonstrate **how far latency can be pushed on the JVM**, with every performance claim backed by reproducible, raw measurement data.

Two things matter equally:

1. **Latency** — deterministic tail latency (p99.9, p99.99, max), not averages or throughput.
2. **Correctness** — matching results must be provably correct and deterministic on replay.

The audience is engineers at Hong Kong banks and trading firms. Code, docs, commit messages and comments are written in **English**.

## Where to look first

| File | Purpose |
|---|---|
| `docs/ROADMAP.md` | Phased task list with acceptance criteria. Tasks are referenced by ID (e.g. `2.3`). |
| `docs/benchmark-methodology.md` | The rules every benchmark must follow. |
| `docs/adr/` | Architecture decisions. Read the relevant ADR before changing anything it covers. |
| `docs/runbooks/` | Step-by-step operational procedures (machine setup, tuning, baselines). |
| `bench/` | Raw benchmark output. Never edit files in here by hand. |

## Workflow rules

- Never start a task marked Conditional in `docs/ROADMAP.md` without explicit approval from the user in the current session.
- Work on **one ROADMAP task at a time**. When a task is done and its acceptance criteria are verified, tick its checkbox in `docs/ROADMAP.md` in the same commit.
- A task is not done until its acceptance criteria are **demonstrated**, not just implemented. If a criterion cannot be verified in the current environment (e.g. it needs the benchmark machine), say so explicitly and leave the box unticked.
- Any new significant technical decision (library choice, threading model, data layout, protocol) gets a new ADR in `docs/adr/` using the next number. Do not silently reverse an existing ADR — write a new one that supersedes it.
- **Ask before adding any new dependency.** The dependency list is deliberately small.
- Commit messages: Conventional Commits (`feat(engine): ...`, `perf(book): ...`, `test(...)`, `docs(adr): ...`, `bench(...)`). Reference the ROADMAP ID in the body.
- Do not modify scripts under `scripts/platform/` in a way that changes the meaning of previously recorded baselines. If a measurement procedure changes, version it and note it in `docs/benchmark-methodology.md`.

## Hot-path rules (non-negotiable)

The **hot path** is everything executed per message between the inbound ring buffer and the outbound ring buffer: decoding, risk checks, matching, event encoding.

On the hot path:

- **Zero allocation in steady state.** No `new`, no autoboxing, no varargs, no lambdas or method references that capture, no `Stream` API, no iterators over collections that allocate, no `String` concatenation or formatting, no `Optional`.
- **No exceptions for control flow.** Rejections are return codes / enum results.
- **No locks, no `synchronized`, no `java.util.concurrent` blocking queues.** Inter-thread handoff uses single-producer/single-consumer ring buffers (Agrona or Disruptor).
- **Single-writer principle.** Every piece of mutable state has exactly one writing thread.
- **No logging** except via a pre-allocated, binary, asynchronous mechanism. No SLF4J/Logback calls on the hot path.
- **No metrics libraries.** No Prometheus/Micrometer calls on the hot path. Latency is recorded with `System.nanoTime()` into per-thread HdrHistogram instances only (see ADR 0001).
- **Primitive collections only** (Agrona collections), pre-sized at startup. No `HashMap<Long, ...>`, no `TreeMap`.
- **No `java.time`, no `System.currentTimeMillis()`** in matching logic. Time is an input carried on the message, so replay is deterministic.
- **Determinism.** Engine logic must not read wall-clock time, use randomness, or depend on hash-map iteration order (Agrona maps included). All timestamps come from input events.
- Prefer flat, cache-friendly data layouts (arrays / off-heap buffers indexed by int) over object graphs.

If a change on the hot path cannot satisfy these rules, stop and explain why before proceeding.

### How allocation-freedom is verified

Claims of "zero allocation" must be backed by at least one of:

- a JMH benchmark run with `-prof gc` showing `gc.alloc.rate.norm ≈ 0 B/op`, and/or
- a steady-state run whose GC log shows **zero GC events** after warm-up for the full measurement window.

Hidden allocations to look for, and the approved alternatives: `docs/zero-allocation.md`.

## Measurement rules (summary — full rules in `docs/benchmark-methodology.md`)

- Load generation is **open-model / constant-rate**. Never send the next request only after the previous response (coordinated omission).
- Report p50, p90, p99, p99.9, p99.99 and **max**, plus sample count. Never report averages alone.
- Every result directory under `bench/` must contain: raw histogram files (`.hlog` / `.hgrm`), GC log, the exact command line, and the output of `scripts/platform/collect-env.sh`.
- Results produced on a development machine, VM or cloud host must be labelled as such and must **not** be quoted as headline numbers. Headline numbers come only from the tuned benchmark machine.
- Performance claims in README or docs must link to the result directory they come from.

## Environment

- **Benchmark machine:** Redmi G (2020), Intel Core i5-10300H (4 cores, SMT disabled via `nosmt`), 16 GB DDR4, internal NVMe, Ubuntu Server 24.04 LTS, headless.
- **Core allocation on the benchmark machine:**

  | CPU | Role |
  |---|---|
  | 0 | Housekeeping: OS, all IRQs, monitoring exporter, histogram snapshot thread |
  | 1 | Load generator |
  | 2 | Matching engine (hot path) |
  | 3 | Aeron media driver + journal |

  CPUs 1–3 are isolated (`isolcpus`, `nohz_full`, `rcu_nocbs`). Thread pinning is done explicitly in code/config, never left to the scheduler.
- **Development:** any machine. Unit tests, correctness tests and JMH smoke runs are fine anywhere; headline latency numbers are not.
- **Monitoring:** Prometheus + Grafana exist on a separate host. They receive *summarised* metrics from the housekeeping thread only (ADR 0001).

## Deployment

- Hot-path processes (`engine-app`, `loadgen`, later `gateway-fix`) run as plain processes on the bare-metal benchmark machine, started by scripts/systemd, with explicit thread pinning (ADR 0004).
- No Docker or Kubernetes on any path used for headline numbers.
- Containers are fine for CI, correctness/integration tests and Phase 6 cluster functional tests; their latency numbers are labelled `exploratory`.

## Tech stack

- Java 25 (LTS) via Gradle toolchains. Gradle Kotlin DSL, version catalog in `gradle/libs.versions.toml`.
- Agrona (buffers, ring buffers, primitive collections, idle strategies), Aeron (IPC/UDP transport, later Archive/Cluster), SBE (message encoding), HdrHistogram, JMH, jqwik (property-based tests), JUnit 5.
- Later phases: Artio (FIX), Aeron Cluster.
- Maven parity is a deliberate later phase (ROADMAP Phase 7, ADR 0003). Until then, Gradle is the only build. Do not add a `pom.xml` before Phase 7.

## Module layout (target)

```
common/        SBE schemas, generated codecs, shared constants
engine-core/   order book + matching logic; pure, single-threaded, no I/O
engine-app/    wiring: ring buffers, threads, affinity, journal, histograms
loadgen/       constant-rate load generator
bench-jmh/     JMH micro-benchmarks
gateway-fix/   (Phase 4) Artio FIX acceptor
scripts/       platform tuning, baseline and benchmark runners
bench/         raw results (committed)
docs/          roadmap, methodology, ADRs, runbooks
```

`engine-core` must not depend on Aeron, threads or clocks — it is a deterministic state machine: `(state, inbound event) -> (state', outbound events)`.

## Commands

(`gateway-fix` is added in Phase 4.)

```
./gradlew build                 # compile + unit tests + spotlessCheck
./gradlew spotlessApply         # format Java sources (palantir-java-format)
./gradlew :engine-core:test     # core tests incl. property-based tests
./gradlew :bench-jmh:jmh        # JMH benchmarks
```
