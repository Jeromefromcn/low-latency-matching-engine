package io.github.jeromefromcn.matching.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

/** Guards the build itself: tests run on the Java 25 toolchain and both test engines are discovered. */
class BuildWiringTest {

    @Test
    void runsOnJava25() {
        assertEquals(25, Runtime.version().feature());
    }

    @Property(tries = 100)
    boolean jqwikEngineIsDiscovered(@ForAll long value) {
        return Long.compare(value, value) == 0;
    }
}
