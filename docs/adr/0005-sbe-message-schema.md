# ADR 0005 — Engine message schema (SBE)

- Status: Accepted
- Date: 2026-10-24

## Context

ROADMAP 2.1 defines the engine's wire format: the inbound requests (NewOrder, CancelOrder) and the outbound events (Accepted, Rejected, Trade, Cancelled). SBE was chosen as the encoding when the stack was set (`CLAUDE.md` → Tech stack): flyweight codecs that read and write fields at fixed offsets in an existing buffer, with no message objects and no parsing step.

This format is the contract between the engine and every producer and consumer: the load generator (3.4), the journal (3.2), and later the FIX gateway (4.x) and market-data publisher (4.3). Journals store these bytes, so the layout cannot change silently once recorded.

## Decision

Schema: `common/src/main/sbe/matching-schema.xml`, id 1, version 0. Codecs are generated on every build into `common/build/generated/sources/sbe` and are never committed.

**Identifiers**

- The client sends an opaque `clientOrderId`. The engine assigns `orderId` (and `tradeId`) from counters in its own state when it accepts an order (or emits a trade), so ids are deterministic on replay.
- `OrderRejected` carries only `clientOrderId`: a rejected order never had an `orderId`. A rejected cancel is a separate message, `CancelRejected`, which is explicitly *not* terminal for the target order. No event can therefore be mistaken for the terminal state of a live order (ROADMAP 2.4 invariant).
- An event's `clientOrderId` is always that of the inbound request that caused it (e.g. an IOC's own `clientOrderId` on the cancel of its unfilled remainder).
- The engine does not check `clientOrderId` for uniqueness. Per-session de-duplication belongs to the gateway / cluster layer (ROADMAP 6.1).

**Order types**

- Two orthogonal fields, as in FIX: `OrdType` {LIMIT, MARKET} × `TimeInForce` {DAY, IOC, FOK}. Which combinations are valid, and how MARKET orders behave, is matching semantics, decided in ROADMAP 2.3.
- `price` is a required field; it must be > 0 for LIMIT and is ignored for MARKET. No SBE optional/null sentinel.

**Encoding rules**

- Prices are `int64` ticks, quantities `int64`. No floating point anywhere.
- All integers are signed. Java has no unsigned `long`; a signed field shows a negative quantity as negative, so the engine can reject it, rather than as a huge positive value.
- Fixed-size fields only, declared widest first (8-, then 4-, then 1-byte) so every field is naturally aligned and blocks have no padding.
- Enum values start at 1, so an all-zero buffer never decodes as a valid side, type or reason.
- `timestamp` is set upstream (gateway / sequencer) and copied onto every event the request causes. The engine never reads a clock.
- Template ids 1–9 are inbound requests, 10–19 outbound events.

**Hot-path decoding rule**

- The generated enum getters (`side()`, `ordType()`, …) throw `IllegalArgumentException` with a concatenated message on an unknown value — an allocation and an exception. The engine reads the raw accessors (`sideRaw()`, …), validates them and turns an invalid value into `OrderRejected` with the matching reason (`INVALID_SIDE`, …).
- Decoders are wrapped with the `blockLength` and `version` from the message header, not the compile-time constants, so that an older decoder can read a newer, extended message.

**Evolution**

- Any change to a field's size, offset or type is a wire-format change: bump the schema `version`. Fields may only be appended to a message, using `sinceVersion`. `WireLayoutTest` pins the block lengths so such a change cannot happen by accident.

## Consequences

- One schema describes the whole engine boundary; codecs are allocation-free flyweights that can wrap ring-buffer and journal memory directly.
- Consumers that need order details on an event (e.g. a gateway building an ExecutionReport) find side, price and quantity on `OrderAccepted`, and otherwise keep their own state keyed by `orderId`. `Trade` carries no per-order remaining quantity; consumers compute it.
- Messages are not padded to 8 bytes (e.g. NewOrder is 8 + 47 bytes). The ring buffer aligns each record to 8 bytes anyway.
- Measurement timestamps (ROADMAP 3.3) are not in the schema. Where stage stamps live is decided with the pipeline (3.1 / 3.3).
- ModifyOrder is not defined yet; it will be added as a new template in a later schema version.

## Alternatives considered

- **Client-supplied `orderId`, engine rejects duplicates:** simpler, but a "duplicate id" reject names a live order, which makes terminal-state accounting ambiguous, and uniqueness checking would need a growing set of every id ever seen.
- **One flat order-type enum** {LIMIT, MARKET, IOC, FOK}: fewer fields, but IOC/FOK must then imply LIMIT, Market-FOK cannot be expressed, and the mapping to FIX (Phase 4) is no longer one-to-one.
- **SBE optional `price` (null = `Long.MIN_VALUE`):** encodes "no price" explicitly, but adds a sentinel every consumer must handle; validation in the engine is simpler and explicit.
- **Committed generated codecs:** reviewable in diffs, but they drift from the schema; generating in the build keeps the schema the single source of truth.
