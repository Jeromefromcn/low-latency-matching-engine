# ADR 0003 — Gradle first, Maven parity later

- Status: Accepted
- Date: 2026-10-22

## Context

Both Maven and Gradle are used across Hong Kong financial institutions. Long-lived bank codebases lean towards Maven (corporate parent POMs, Nexus/Artifactory, existing CI), while the low-latency open-source ecosystem this project builds on (Aeron, Agrona, Artio, LMAX Disruptor) uses Gradle.

Maintaining two builds from day one doubles every build change while the module layout is still moving, and risks silent divergence (different conflict-resolution rules: Maven picks the nearest version, Gradle the highest).

## Decision

1. **Phase 0–6:** Gradle (Kotlin DSL) is the only build. Versions live in `gradle/libs.versions.toml`. JMH via the `me.champeau.jmh` plugin.
2. **Phase 7 (after v0.1 is tagged and modules are stable):** add an equivalent Maven build that is kept in parity by:
   - a script comparing resolved runtime dependency lists of every module from both builds, failing on any difference;
   - a CI matrix running both builds and all tests;
   - a `CLAUDE.md` rule that every build change is applied to both builds in the same commit.

## Consequences

- Fast iteration while the design is fluid.
- The final repository demonstrates fluency in both build tools, with a mechanical guarantee of parity rather than a promise.
- Until Phase 7, no `pom.xml` exists in the repository.

## Alternatives considered

- **Maven only:** closer to typical bank codebases, but diverges from the reference projects' build setups and makes JMH/multi-module ergonomics slightly heavier.
- **Dual builds from day one:** rejected for the maintenance and drift reasons above.
