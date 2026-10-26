package com.smarthome;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

/** Confirms Mockito can create mocks on the JDK running the build. */
class MockitoCompatibilityTest {

    @Test
    void mockitoWorksOnThisJdk() {
        Runnable runnable = mock(Runnable.class);
        runnable.run();
        verify(runnable).run();
    }
}