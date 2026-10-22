package io.github.jeromefromcn.matching.bench;

import org.openjdk.jmh.annotations.Benchmark;

/**
 * Empty benchmark. Its {@code gc.alloc.rate.norm} under {@code -prof gc} is the JMH-infrastructure
 * allocation floor that engine benchmarks are compared against (ROADMAP 2.7).
 */
public class BaselineBenchmark {

    @Benchmark
    public void baseline() {}
}
