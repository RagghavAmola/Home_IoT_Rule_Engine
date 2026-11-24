package com.smarthome.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppBootstrapTest {

    private AppBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap = AppBootstrap.start();
    }

    @AfterEach
    void tearDown() {
        if (bootstrap != null) {
            bootstrap.shutdown();
        }
    }

    @Test
    @DisplayName("AppBootstrap initializes all DAOs, services, simulation store, and background monitors")
    void testBootstrapInitializationAndWiring() {
        assertNotNull(bootstrap.getDeviceControlService());
        assertNotNull(bootstrap.getRuleValidationService());
        assertNotNull(bootstrap.getRuleMonitorService());
        assertNotNull(bootstrap.getStateStore());
        assertNotNull(bootstrap.getRuleCache());
        assertNotNull(bootstrap.getDependencyGraph());
        assertNotNull(bootstrap.getExecutionLogDao());

        assertTrue(bootstrap.getRuleMonitorService().isRunning());
        assertFalse(bootstrap.getRuleMonitorService().isShutdown());

        // Verify seeded devices were loaded into state store
        assertTrue(bootstrap.getStateStore().size() > 0);
    }
}

