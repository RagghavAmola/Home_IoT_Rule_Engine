package com.smarthome.simulation;

import com.smarthome.domain.Action;
import com.smarthome.engine.StateSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceStateStoreTest {

    private DeviceStateStore store;

    @BeforeEach
    void setUp() {
        store = new DeviceStateStore();
    }

    @Test
    @DisplayName("Set, get, and snapshot operations function correctly")
    void testBasicStateOperations() {
        assertNull(store.getState("light-1", "power"));

        boolean changed1 = store.setState("light-1", "power", "ON");
        assertTrue(changed1);
        assertEquals("ON", store.getState("light-1", "power"));

        // Setting same value again returns false (no state change)
        boolean changed2 = store.setState("light-1", "power", "ON");
        assertFalse(changed2);

        StateSnapshot snapshot = store.snapshot();
        assertEquals("ON", snapshot.get("light-1", "power"));

        // Modifying store does not alter prior snapshot
        store.setState("light-1", "power", "OFF");
        assertEquals("OFF", store.getState("light-1", "power"));
        assertEquals("ON", snapshot.get("light-1", "power"), "Snapshot must remain detached and immutable");
    }

    @Test
    @DisplayName("applyAction updates store and enforces registered device validation")
    void testApplyActionWithValidation() {
        SimulatedLight light = new SimulatedLight("light-1", "Ceiling", "Room");
        store.registerDevice(light);

        // Default state populated
        assertEquals("OFF", store.getState("light-1", "power"));
        assertEquals("0", store.getState("light-1", "brightness"));

        // Valid action applies successfully
        Action validAction = new Action("light-1", "brightness", "75");
        boolean applied = store.applyAction(validAction);
        assertTrue(applied);
        assertEquals("75", store.getState("light-1", "brightness"));

        // Invalid action for light throws IllegalArgumentException
        Action invalidAction = new Action("light-1", "brightness", "999");
        assertThrows(IllegalArgumentException.class, () -> store.applyAction(invalidAction));
        assertEquals("75", store.getState("light-1", "brightness"), "State must remain unchanged after rejected action");
    }

    @Test
    @DisplayName("StateChangeListener receives change events on state mutations")
    void testStateChangeListeners() {
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        StateChangeListener listener = (key, oldVal, newVal) ->
                events.add(key + ": " + oldVal + " -> " + newVal);

        store.addListener(listener);
        assertEquals(1, store.listenerCount());

        store.setState("door-1", "status", "LOCKED");
        store.setState("door-1", "status", "LOCKED"); // duplicate, no notification
        store.setState("door-1", "status", "UNLOCKED");

        assertEquals(2, events.size());
        assertEquals("door-1.status: null -> LOCKED", events.get(0));
        assertEquals("door-1.status: LOCKED -> UNLOCKED", events.get(1));

        store.removeListener(listener);
        assertEquals(0, store.listenerCount());

        store.setState("door-1", "status", "LOCKED");
        assertEquals(2, events.size(), "Deregistered listener must not receive further events");
    }

    @Test
    @DisplayName("DeviceStateStore is safe under concurrent writers (ExecutorService + CountDownLatch)")
    void testConcurrentWriters() throws InterruptedException {
        int threadCount = 20;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successfulWrites = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        // Register a state change listener tracking notification throughput concurrently
        AtomicInteger listenerInvocations = new AtomicInteger(0);
        store.addListener((key, oldVal, newVal) -> listenerInvocations.incrementAndGet());

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await(); // wait for gun
                    for (int i = 0; i < operationsPerThread; i++) {
                        String deviceId = "dev-" + (i % 10);
                        String stateKey = "key-" + (threadId % 5);
                        String value = "val-" + threadId + "-" + i;

                        store.setState(deviceId, stateKey, value);
                        successfulWrites.incrementAndGet();

                        // Occasionally take snapshot concurrently while other threads write
                        if (i % 100 == 0) {
                            StateSnapshot snap = store.snapshot();
                            assertNotNull(snap);
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // fire all threads
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(finished, "All concurrent writers should complete within timeout");
        assertEquals(0, errors.get(), "No exceptions should occur during concurrent writes");
        assertEquals(threadCount * operationsPerThread, successfulWrites.get(), "All writes should complete");
        assertTrue(store.size() > 0, "Store should contain device attributes");
        assertTrue(listenerInvocations.get() > 0, "Listeners should be notified safely across concurrent threads");
    }
}

