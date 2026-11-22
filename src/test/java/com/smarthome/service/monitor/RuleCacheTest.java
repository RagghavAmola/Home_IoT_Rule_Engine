package com.smarthome.service.monitor;

import com.smarthome.dao.RuleDao;
import com.smarthome.domain.Action;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.Operator;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleCacheTest {

    private Rule createSampleRule(String id, boolean enabled) {
        return new Rule(
                id,
                "Rule " + id,
                enabled,
                PriorityTier.COMFORT,
                0,
                new LeafCondition("light-1", "power", Operator.EQ, "ON"),
                List.of(new Action("siren-1", "alarm", "ON")),
                Instant.now()
        );
    }

    @Test
    @DisplayName("RuleCache stores rules and filters only enabled rules")
    void testBasicCacheAndFilterEnabled() {
        RuleCache cache = new RuleCache();
        assertEquals(0, cache.size());
        assertTrue(cache.getEnabledRules().isEmpty());

        Rule r1 = createSampleRule("R1", true);
        Rule r2 = createSampleRule("R2", false);
        Rule r3 = createSampleRule("R3", true);

        cache.updateRule(r1);
        cache.updateRule(r2);
        cache.updateRule(r3);

        assertEquals(3, cache.size());
        assertEquals(2, cache.getEnabledRules().size());

        Optional<Rule> found = cache.getRule("R2");
        assertTrue(found.isPresent());
        assertFalse(found.get().isEnabled());

        cache.removeRule("R1");
        assertEquals(2, cache.size());
        assertEquals(1, cache.getEnabledRules().size());
        assertEquals("R3", cache.getEnabledRules().get(0).getId());
    }

    @Test
    @DisplayName("refresh() reloads all rules from RuleDao")
    void testRefreshFromDao() {
        RuleDao ruleDao = Mockito.mock(RuleDao.class);
        Rule r1 = createSampleRule("R1", true);
        Rule r2 = createSampleRule("R2", true);
        when(ruleDao.findAll()).thenReturn(List.of(r1, r2));

        RuleCache cache = new RuleCache(ruleDao);
        int enabledLoaded = cache.refresh();

        assertEquals(2, enabledLoaded);
        assertEquals(2, cache.size());
        assertEquals(2, cache.getEnabledRules().size());
        verify(ruleDao).findAll();
    }

    @Test
    @DisplayName("Rule cache updates during concurrent evaluations are completely thread-safe")
    void testConcurrentCacheReadsAndWrites() throws InterruptedException {
        RuleCache cache = new RuleCache();
        for (int i = 0; i < 20; i++) {
            cache.updateRule(createSampleRule("init-" + i, true));
        }

        int readerThreads = 10;
        int writerThreads = 5;
        int iterations = 300;
        ExecutorService executor = Executors.newFixedThreadPool(readerThreads + writerThreads);

        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(readerThreads + writerThreads);
        AtomicInteger readErrors = new AtomicInteger(0);
        AtomicInteger writeErrors = new AtomicInteger(0);

        // Readers repeatedly reading enabled rules snapshot
        for (int r = 0; r < readerThreads; r++) {
            executor.submit(() -> {
                try {
                    startGun.await();
                    for (int i = 0; i < iterations; i++) {
                        List<Rule> enabled = cache.getEnabledRules();
                        assertNotNull(enabled);
                        // Iterate through snapshot; must never throw ConcurrentModificationException
                        int count = 0;
                        for (Rule rule : enabled) {
                            if (rule.isEnabled()) {
                                count++;
                            }
                        }
                        assertTrue(count >= 0);
                    }
                } catch (Throwable t) {
                    readErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Writers concurrently updating, adding, removing rules
        for (int w = 0; w < writerThreads; w++) {
            final int writerId = w;
            executor.submit(() -> {
                try {
                    startGun.await();
                    for (int i = 0; i < iterations; i++) {
                        String id = "w-" + writerId + "-" + (i % 10);
                        boolean enabled = (i % 2 == 0);
                        cache.updateRule(createSampleRule(id, enabled));
                        if (i % 5 == 0) {
                            cache.removeRule(id);
                        }
                    }
                } catch (Throwable t) {
                    writeErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGun.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrent test must complete within timeout");
        assertEquals(0, readErrors.get(), "Readers must experience zero errors or corruption");
        assertEquals(0, writeErrors.get(), "Writers must experience zero errors");
    }
}

