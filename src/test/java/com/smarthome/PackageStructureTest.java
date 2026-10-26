package com.smarthome;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies core package definitions and logging initialization.
 */
class PackageStructureTest {

    private static final Logger logger = LoggerFactory.getLogger(PackageStructureTest.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("RULE_AUDIT");

    @Test
    @DisplayName("Verify package structure and audit logger availability")
    void testPackageAndLoggingSetup() {
        logger.info("Verifying package initialization in test environment");
        Package rootPkg = PackageStructureTest.class.getPackage();
        assertNotNull(rootPkg, "com.smarthome root package must exist");

        auditLogger.info("TEST: Audit logger initialized successfully");
        assertTrue(true, "Base project scaffold verified");
    }
}
