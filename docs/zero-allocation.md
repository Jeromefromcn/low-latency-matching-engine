# Zero allocation on the hot path

Checklist for reviewing hot-path code (definition and rules: `CLAUDE.md` → Hot-path rules). Escape analysis sometimes removes these allocations, but it is not guaranteed and changes with inlining decisions — do not rely on it.

## Hidden allocations

| Pattern | Why it allocates | Approved alternative |
|---|---|---|
| Autoboxing (`Long`, `Integer` in collections, generics, `Object` parameters) | `valueOf` allocates outside the small cache (−128..127) | Agrona primitive collections (`Long2LongHashMap`, `Long2ObjectHashMap`, `LongArrayList`); primitive fields and parameters |
| String concatenation, `String.format`, `toString()` | Builds new `String` / `StringBuilder` / `byte[]` | No strings on the hot path; encode fields into SBE messages or binary log records |
| for-each over a `Collection` | Allocates an `Iterator` per loop | Indexed loops over arrays; Agrona collections with cached iterators (`shouldAvoidAllocation = true`, the default) |
| Capturing lambdas and method references | New instance per evaluation when they capture state | Pre-allocated handler objects held in fields; non-capturing lambdas only if verified |
| Varargs (incl. logging calls) | Allocates an `Object[]` per call, plus boxing | Fixed-arity methods; pre-allocated binary async logging only |
| `Enum.values()` | Returns a cloned array on every call | Cache `values()` once in a `static final` array |
| Exceptions | Object plus stack-trace capture | Return codes / enum results; no exceptions for control flow |
| Collection resizing | Grows backing arrays on `add`/`put` past capacity | Pre-size every collection at start-up for the configured maximum; treat reaching capacity as a reject, not a resize |
| `new` of per-message objects (orders, events) | One object per message | Pools / free lists of pre-allocated nodes, indexed by `int` |
| `ByteBuffer.wrap`, `Arrays.asList`, `Optional`, streams | Wrapper objects per call | Agrona `UnsafeBuffer` reused via `wrap`; SBE flyweights |

## Approved building blocks

- **Pools / free lists** — order nodes and similar objects pre-allocated at start-up and recycled.
- **Agrona primitive collections** — pre-sized, no boxing, no per-iteration allocation.
- **SBE flyweights** — encoders/decoders wrap an existing buffer; no message objects.
- **Off-heap buffers** — `UnsafeBuffer` over direct or memory-mapped memory for rings, journals and book storage.

## Verification

A review against this list is not proof. Allocation-freedom is demonstrated by:

- JMH with `-prof gc`: `gc.alloc.rate.norm` = 0 B/op for hot-path benchmarks (ROADMAP 2.7, 3.1);
- a GC log with zero collections during the measured window (ROADMAP 3.6);
- an Epsilon GC run (`-XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC`, small heap) processing several hundred million messages without `OutOfMemoryError` (ROADMAP 3.6).
