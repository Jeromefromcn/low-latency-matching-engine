# ADR 0002 — Use in-process ring buffers / Aeron instead of Kafka for the message path

- Status: Accepted
- Date: 2026-10-22

## Context

Kafka is the default event backbone in large-scale internet systems and is familiar from prior production work. The question is whether it belongs on this engine's message path.

Kafka is optimised for durable, high-throughput, horizontally scaled streaming. Its per-message latency is dominated by network round-trips to brokers, batching (`linger.ms`), page-cache flushes and consumer polling — typically in the **millisecond** range end-to-end, with a tail driven by broker GC and replication. The engine's budget is in **microseconds**.

## Decision

- Between threads in the same process: single-producer/single-consumer ring buffers (Agrona `OneToOneRingBuffer` or the LMAX Disruptor), with a busy-spin idle strategy on isolated cores.
- Between processes on the same host: Aeron IPC (shared memory, no system calls on the data path).
- Between hosts (market data, later clustering): Aeron UDP.
- Durability: an append-only journal written in sequence (Aeron Archive or Chronicle Queue — to be decided in a later ADR), not a broker.

## Consequences

- No broker hop on the hot path; latency bounded by memory and CPU cache behaviour.
- We give up Kafka's operational conveniences (consumer groups, retention, rebalancing). Downstream consumers that need those (e.g. analytics) can be fed asynchronously from the journal, off the hot path.
- A planned experiment (Phase 5) quantifies the difference by placing Kafka on the path and comparing distributions under the same methodology.

## Alternatives considered

- **Kafka with aggressive tuning** (`linger.ms=0`, `acks=1`, local broker): still a network/syscall path and broker GC in the tail.
- **Chronicle Queue for IPC as well as journal:** viable; deferred to the journal ADR.
