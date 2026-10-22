/**
 * Order book and matching logic: a pure, single-threaded, deterministic state machine
 * {@code (state, inbound event) -> (state', outbound events)} with no I/O, threads or clocks.
 */
package io.github.jeromefromcn.matching.engine;
