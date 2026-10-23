package io.github.jeromefromcn.matching.app;

import org.HdrHistogram.SingleWriterRecorder;

/** A latency recorder and the tag its intervals carry in the {@code .hlog}. One writer thread each. */
record TaggedRecorder(String tag, SingleWriterRecorder recorder) {}
