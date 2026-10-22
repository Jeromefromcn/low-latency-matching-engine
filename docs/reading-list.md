# Reading List

Ordered to match the roadmap. Tick items as you read them and add a one-line takeaway — those notes become interview material.

## Before Phase 1 — paradigm and measurement

- [ ] Martin Fowler — *The LMAX Architecture* (martinfowler.com)
  - Takeaway:
- [ ] Martin Thompson et al. — *Disruptor: High performance alternative to bounded queues for exchanging data between concurrent threads* (LMAX technical paper, 2011)
  - Takeaway:
- [ ] Gil Tene — talk *How NOT to Measure Latency* (search for the recorded conference version)
  - Takeaway:
- [ ] HdrHistogram documentation and README — especially the sections on coordinated omission and `Recorder`
  - Takeaway:
- [ ] Martin Thompson — *Mechanical Sympathy* blog: *False Sharing*, *Memory Barriers/Fences*, *Single Writer Principle*, *Smart Batching*
  - Takeaway:

## Phase 1 — operating system and hardware

- [ ] Red Hat — low-latency / real-time tuning guide for RHEL (isolcpus, nohz_full, IRQ affinity, `tuned` profiles)
  - Takeaway:
- [ ] Mark Price — blog series on reducing system jitter
  - Takeaway:
- [ ] Ulrich Drepper — *What Every Programmer Should Know About Memory* (CPU cache chapters only)
  - Takeaway:
- [ ] Brendan Gregg — *Systems Performance* (CPU and memory chapters) and his flame graph articles
  - Takeaway:

## Phase 2 — domain

- [ ] Larry Harris — *Trading and Exchanges* (first half: market structure, order types, order books)
  - Takeaway:
- [ ] FIX 4.4 specification — NewOrderSingle (35=D), OrderCancelRequest (35=F), ExecutionReport (35=8)
  - Takeaway:
- [ ] HKEX public interface specifications — OCG-C (order gateway) and OMD-C (market data); skim message flow and session handling
  - Takeaway:

## Phase 3 — JVM and concurrency

- [ ] Aleksey Shipilëv — *JVM Anatomy Quarks* series
  - Takeaway:
- [ ] JMH samples (`JMHSample_*`) — run every one, not just read
  - Takeaway:
- [ ] Nitsan Wakart — *Psy Lob Saw* blog (lock-free queues, JCTools internals)
  - Takeaway:
- [ ] Peter Lawrey — *Vanilla Java* / Chronicle blog (off-heap, GC-free Java)
  - Takeaway:
- [ ] Aeron wiki (architecture, IPC, idle strategies, media driver threading modes) and Agrona documentation
  - Takeaway:
- [ ] SBE (Simple Binary Encoding) wiki — schema design and flyweight codecs
  - Takeaway:

## Phase 4–6

- [ ] Artio documentation (FIX engine on Aeron)
  - Takeaway:
- [ ] Aeron Cluster documentation (Raft-based replicated state machine)
  - Takeaway:
- [ ] Martin Kleppmann — *Designing Data-Intensive Applications*, chapters on replication and consensus (refresher)
  - Takeaway:
