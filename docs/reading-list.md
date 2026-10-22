# Reading List

Ordered to match the roadmap. Tick items as you read them and add a one-line takeaway — those notes become interview material.

Links were checked on 2026-10-22. Books are listed with a publisher or author page, not a download.

## Before Phase 1 — paradigm and measurement

- [ ] Martin Fowler — *The LMAX Architecture*
  - https://www.martinfowler.com/articles/lmax.html
  - Takeaway:
- [ ] Martin Thompson et al. — *Disruptor: High performance alternative to bounded queues for exchanging data between concurrent threads* (LMAX technical paper, 2011)
  - https://lmax-exchange.github.io/disruptor/disruptor.html
  - Takeaway:
- [ ] Gil Tene — *How NOT to Measure Latency* (talk; coordinated omission)
  - https://www.infoq.com/presentations/latency-response-time
  - Takeaway:
- [ ] HdrHistogram — README, especially coordinated omission and `Recorder`
  - https://github.com/HdrHistogram/HdrHistogram
  - Takeaway:
- [ ] Martin Thompson — *Mechanical Sympathy* blog, core posts:
  - [ ] *False Sharing* — https://mechanical-sympathy.blogspot.com/2011/07/false-sharing.html
  - [ ] *Memory Barriers/Fences* — https://mechanical-sympathy.blogspot.com/2011/07/memory-barriersfences.html
  - [ ] *Single Writer Principle* — https://mechanical-sympathy.blogspot.com/2011/09/single-writer-principle.html
  - [ ] *Smart Batching* — https://mechanical-sympathy.blogspot.com/2011/10/smart-batching.html
  - [ ] *Memory Access Patterns Are Important* — https://mechanical-sympathy.blogspot.com/2012/08/memory-access-patterns-are-important.html
  - [ ] *Inter-Thread Latency* — https://mechanical-sympathy.blogspot.com/2011/08/inter-thread-latency.html
  - Takeaway:
- [ ] *Principles of Mechanical Sympathy* (martinfowler.com, 2026) — a modern summary of the posts above
  - https://martinfowler.com/articles/mechanical-sympathy-principles.html
  - Takeaway:

## Phase 1 — operating system and hardware

- [ ] Red Hat — *Optimizing RHEL 9 for Real Time for low latency operation* (isolcpus, nohz_full, IRQ affinity, tuned profiles)
  - https://docs.redhat.com/en/documentation/red_hat_enterprise_linux_for_real_time/9/html-single/optimizing_rhel_9_for_real_time_for_low_latency_operation/index
  - Takeaway:
- [ ] Mark Price — *Reducing system jitter*, parts 1 and 2, plus the hands-on workshop
  - Part 1: https://epickrram.blogspot.com/2015/09/reducing-system-jitter.html
  - Part 2: https://epickrram.blogspot.com/2015/11/reducing-system-jitter-part-2.html
  - Workshop: https://github.com/epickrram/perf-workshop
  - Takeaway:
- [ ] Ulrich Drepper — *What Every Programmer Should Know About Memory* (CPU cache chapters only)
  - LWN page with the PDF and the serialized version: https://lwn.net/Articles/259710/
  - Takeaway:
- [ ] Brendan Gregg — *Systems Performance*, 2nd ed. (CPU and memory chapters) and CPU flame graphs
  - Book: https://brendangregg.com/systems-performance-2nd-edition-book.html
  - Flame graphs: https://www.brendangregg.com/FlameGraphs/cpuflamegraphs.html
  - Takeaway:

## Phase 2 — domain

- [ ] Larry Harris — *Trading and Exchanges: Market Microstructure for Practitioners* (first half: market structure, order types, order books)
  - https://academic.oup.com/book/52292
  - Takeaway:
- [ ] FIX 4.4 — NewOrderSingle (35=D), OrderCancelRequest (35=F), ExecutionReport (35=8)
  - Readable message dictionary: https://onixs.biz/fix-dictionary/4.4/msgType_D_68.html (navigate to 35=F and 35=8 from there)
  - Takeaway:
- [ ] HKEX OCG-C (order gateway) — skim message flow, order types, session handling
  - Overview: https://www.hkex.com.hk/-/media/HKEX-Market/Services/Trading/Securities/Infrastructure/OCGC/OCGC-Overview.pdf
  - FIX interface spec (v3.1, 2021): https://www.hkex.com.hk/-/media/HKEX-Market/Services/Trading/Securities/Infrastructure/OCGC/HKEX_OCGC_FIX_Trading_Interface_Specifications_v3_1-(20210923).pdf
  - Binary interface spec (v3.0, 2021): https://www.HKEX.com.hk/-/media/HKEX-Market/Services/Trading/Securities/Infrastructure/OCGC/HKEX_OCGC_Binary_Trading_Interface_Specifications_v3_0-(20210917).pdf
  - Newer versions may exist; check the securities infrastructure page: https://www.hkex.com.hk/Services/Trading/Securities/Infrastructure/Overview
  - Takeaway:
- [ ] HKEX OMD-C (securities market data) — skim message types and book-building rules
  - Binary interface spec v1.39: https://www.hkex.com.hk/chi/prod/dataprod/Documents/HKEX_OMD-C_Binary_Interface_Specifications_v1.39%20(final).pdf
  - Takeaway:

## Domain — securities financing and prime brokerage

- [ ] Margin lending and margin calls — source: SFC, [*Guidelines for Securities Margin Financing Activities*](https://www.sfc.hk/en/Rules-and-standards/Codes-and-guidelines/Guidelines/Guidelines-for-Securities-Margin-Financing-Activities) (in effect since October 2019; margin call triggers, haircuts, concentration limits)
  - Takeaway:
- [ ] Securities borrowing and lending lifecycle (locate, borrow, recall, return) — source: Mark C. Faulkner, *An Introduction to Securities Lending* (commissioned by ISLA; also Chapter 1 of Fabozzi & Mann, *Securities Finance*, below)
  - Takeaway:
- [ ] Fees and rebate rates — source: Frank J. Fabozzi and Steven V. Mann (eds.), *Securities Finance: Securities Lending and Repurchase Agreements* (Wiley, 2005), Part 1
  - Takeaway:
- [ ] Collateral management — source: Michael Simmons, *Collateral Management: A Guide to Mitigating Counterparty Risk* (Wiley, 2019)
  - Takeaway:
- [ ] Financing (repo basics) — source: ICMA, [*Frequently Asked Questions on Repo*](https://www.icmagroup.org/frequently-asked-questions-on-repo) (January 2019 edition)
  - Takeaway:
- [ ] Position and trade reconciliation — source: Michael Simmons, *Securities Operations: A Guide to Trade and Position Management* (Wiley, 2002)
  - Takeaway:
- [ ] Short-selling rules in Hong Kong — source: SFC, [Short position reporting](https://sfc.hk/en/Forms/Trading/Short-position-reporting) (user guide, FAQs, list of specified shares); background: naked short selling ban under SFO s.170
  - Takeaway:

## Phase 3 — JVM and concurrency

- [ ] Aleksey Shipilëv — *JVM Anatomy Quarks* (complete PDF)
  - https://shipilev.net/jvm/anatomy-quarks/jvm-anatomy-quarks-complete.pdf
  - Takeaway:
- [ ] JMH samples (`JMHSample_*`, 37 files) — run every one, not just read
  - https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples
  - Takeaway:
- [ ] Nitsan Wakart — *Psy Lob Saw* blog (lock-free queues, JCTools internals, safepoints) and JCTools
  - Blog: https://psy-lob-saw.blogspot.com/ — start with *Safepoints*: https://psy-lob-saw.blogspot.com/2015/12/safepoints.html
  - JCTools: https://github.com/JCTools/JCTools
  - Takeaway:
- [ ] Peter Lawrey — *Vanilla Java* blog (off-heap, GC-free Java)
  - https://blog.vanillajava.blog/ (older posts: http://vanillajava.blogspot.com/)
  - Takeaway:
- [ ] Aeron wiki — architecture, IPC, idle strategies, threading modes
  - Home: https://github.com/aeron-io/aeron/wiki
  - Best practices: https://github.com/aeron-io/aeron/wiki/Best-Practices-Guide
  - Thread utilisation: https://github.com/aeron-io/aeron/wiki/Thread-Utilisation
  - Performance testing: https://github.com/aeron-io/aeron/wiki/Performance-Testing
  - Takeaway:
- [ ] Agrona wiki
  - https://github.com/aeron-io/agrona/wiki
  - Takeaway:
- [ ] SBE (Simple Binary Encoding) wiki — schema design and flyweight codecs
  - https://github.com/aeron-io/simple-binary-encoding/wiki
  - Takeaway:
- [ ] jHiccup (used in Phase 1 baselines)
  - https://github.com/giltene/jHiccup
  - Takeaway:

## Phase 4–6 (conditional)

- [ ] Artio wiki (FIX engine on Aeron)
  - https://github.com/real-logic/artio/wiki
  - Takeaway:
- [ ] Aeron Cluster tutorial and component model
  - https://github.com/aeron-io/aeron/wiki/Cluster-Tutorial
  - https://github.com/aeron-io/aeron/wiki/Cluster-Component-Model
  - Takeaway:
- [ ] Martin Kleppmann — *Designing Data-Intensive Applications*, chapters on replication and consensus (refresher)
  - https://www.oreilly.com/library/view/designing-data-intensive-applications/9781098119058/
  - Takeaway:

## Market structure background (optional, interview context)

- [ ] Aquilina, Budish, O'Neill — *Quantifying the High-Frequency Trading "Arms Race"* (latency arbitrage, races on the LSE)
  - https://www.nber.org/system/files/working_papers/w29011/w29011.pdf
  - Takeaway:
- [ ] IEX — how the 350 µs speed bump works
  - https://www.iex.io/technology
  - Takeaway: