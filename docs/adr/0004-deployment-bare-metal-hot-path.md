# ADR 0004 — Hot-path processes run on bare metal, not in containers

- Status: Accepted
- Date: 2026-10-22

## Context

Containers and Kubernetes are the default deployment form in most modern systems, and the obvious question is whether this engine should use them. The engine targets microsecond-level tail latency on a 4-core machine where each core has a fixed role (see `CLAUDE.md` → Environment). Anything that adds a scheduling layer, a virtual network hop or a CPU quota sits directly on the measured path.

Industry practice shows that the deployment form follows the latency target:

- **HKEX Hosting Services** ([service offerings](https://www.hkex.com.hk/Services/Connectivity/Hosting-Services/Service-Offerings), [Hosting Information Package, Dec 2023](https://www.hkex.com.hk/-/media/HKEX-Market/Services/Connectivity/Hosting-Services/Information-package-20231208/HKEX-Hosting-Information-Package-v,-d-,4_e.pdf)) — co-location at the HKEX data centre in Tseung Kwan O Industrial Estate. Subscribers install their own equipment in their racks; equidistant cabling on the HKEX Service Network (HSN) gives practically the same latency to OCG-C, OMD and the other core systems regardless of rack location; NTP and PTP (IEEE 1588) timing is provided; equipment installation is generally permitted only outside normal HKEX trading hours.
- **Crypto venues span the whole spectrum:**
  - BitMEX runs its trading engine on Amazon EKS, with order execution under 10 ms ([AWS case study](https://aws.amazon.com/solutions/case-studies/bitmex/)).
  - Coinbase runs its trading engine on EC2 z1d instances in cluster placement groups ([AWS case study](https://aws.amazon.com/it/solutions/case-studies/coinbase-cryptocurrency-exchange-case-study/)).
  - One Trading tested its exchange and a market-maker client on EC2 c6id.metal instances in a cluster placement group shared between the two AWS accounts, pre-launch, with a round-trip target under 200 µs ([AWS blog, Dec 2023](https://aws.amazon.com/blogs/industries/one-trading-exchange-and-aws-cloud-native-colocation-for-crypto-trading/)).
  - Deribit's Starbase matching engine is reachable only via hosted colocation or cross-connect in Equinix LD4 (or AWS PrivateLink), not over the internet ([Starbase overview](https://docs.deribit.com/starbase/overview), [announcement](https://insights.deribit.com/exchange-updates/starbase-a-new-era-of-high-performance-trading-on-deribit/)).

**Takeaway:** millisecond-level engines run comfortably on managed containers; microsecond-level engines end up on bare metal, owned or cloud-provided, with placement controlled explicitly.

## Decision

- Hot-path processes (`engine-app`, `loadgen`, later `gateway-fix`) run as **plain processes on the bare-metal benchmark machine**, started by scripts / systemd units, with **explicit thread pinning** per the core-allocation table in `CLAUDE.md`.
- **No Docker or Kubernetes** on any path used for headline numbers.
- Containers are allowed for CI, correctness/integration tests and Phase 6 cluster functional tests. Latency numbers from containers are labelled `exploratory` (`docs/benchmark-methodology.md` §6).

## Consequences

- No container runtime, overlay network or cgroup CPU quota between the measured threads and the hardware; thread-to-core placement is exactly what the code and config say.
- Deployment is less portable: start-up scripts / systemd units for the benchmark machine are part of the repository and must be kept working.
- The premise of this ADR is testable: ROADMAP 5.9 (conditional) compares bare metal against correctly configured and default Docker under the same methodology.

## Alternatives considered

- **Docker with host networking, host IPC and `--cpuset-cpus`, no CPU quota:** likely close to bare metal, but adds a layer whose effect must itself be measured before trusting it. Deferred to experiment 5.9.
- **Kubernetes with the static CPU manager policy and guaranteed QoS pods:** adds kubelet, CNI and node-level daemons to a 4-core machine with no spare cores for them.
