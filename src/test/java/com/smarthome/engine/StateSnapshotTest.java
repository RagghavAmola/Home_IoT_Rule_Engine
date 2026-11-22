package com.smarthome.engine;

import com.smarthome.domain.DeviceStateKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateSnapshotTest {

    @Test
    @DisplayName("Empty snapshot behaves correctly")
    void testEmptySnapshot() {
        StateSnapshot empty = StateSnapshot.empty();
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.size());
        assertNull(empty.get("light-1", "power"));
        assertNull(empty.get(new DeviceStateKey("light-1", "power")));
    }

    @Test
    @DisplayName("Snapshot from map is immutable and isolated from subsequent map mutations")
    void testSnapshotImmutabilityAndIsolation() {
        Map<DeviceStateKey, String> map = new HashMap<>();
        DeviceStateKey key1 = new DeviceStateKey("light-1", "power");
        map.put(key1, "OFF");

        StateSnapshot snapshot = StateSnapshot.of(map);
        assertEquals("OFF", snapshot.get(key1));
        assertEquals("OFF", snapshot.get("light-1", "power"));

        // Mutate original map
        map.put(key1, "ON");
        assertEquals("OFF", snapshot.get(key1), "Snapshot must remain unchanged when source map is mutated");

        // Attempt mutations directly on snapshot
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(key1, "ON"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.remove(key1));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    @DisplayName("with() creates new snapshot instance preserving immutability")
    void testWithCreatesNewSnapshot() {
        StateSnapshot initial = StateSnapshot.empty();
        StateSnapshot updated = initial.with("thermostat-1", "temperature", "22.5");

        assertEquals(0, initial.size());
        assertEquals(1, updated.size());
        assertEquals("22.5", updated.get("thermostat-1", "temperature"));

        StateSnapshot multi = updated.with(new DeviceStateKey("light-1", "power"), "ON");
        assertEquals(2, multi.size());
        assertEquals("22.5", multi.get("thermostat-1", "temperature"));
        assertEquals("ON", multi.get("light-1", "power"));
    }

    @Test
    @DisplayName("Map delegation methods work correctly")
    void testMapDelegation() {
        DeviceStateKey key = new DeviceStateKey("plug-1", "power");
        StateSnapshot snapshot = StateSnapshot.empty().with(key, "ON");

        assertTrue(snapshot.containsKey(key));
        assertFalse(snapshot.containsKey(new DeviceStateKey("plug-2", "power")));
        assertTrue(snapshot.containsValue("ON"));
        assertFalse(snapshot.containsValue("OFF"));
        assertEquals(1, snapshot.keySet().size());
        assertEquals(1, snapshot.values().size());
        assertEquals(1, snapshot.entrySet().size());
    }
}

