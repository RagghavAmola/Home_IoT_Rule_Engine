package com.smarthome.simulation;

import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedDeviceTest {

    @Test
    @DisplayName("SimulatedLight validates power and brightness correctly")
    void testLightValidation() {
        SimulatedLight light = new SimulatedLight("light-1", "Ceiling Light", "Living Room");
        assertEquals(DeviceType.LIGHT, light.getType());

        assertTrue(light.isValidState("power", "ON"));
        assertTrue(light.isValidState("power", "OFF"));
        assertTrue(light.isValidState("power", "on"));
        assertFalse(light.isValidState("power", "INVALID"));

        assertTrue(light.isValidState("brightness", "50"));
        assertTrue(light.isValidState("brightness", "0"));
        assertTrue(light.isValidState("brightness", "100"));
        assertFalse(light.isValidState("brightness", "-1"));
        assertFalse(light.isValidState("brightness", "101"));
        assertFalse(light.isValidState("brightness", "abc"));

        assertThrows(IllegalArgumentException.class, () -> light.validateState("power", "MAYBE"));
        assertThrows(IllegalArgumentException.class, () -> light.validateState("brightness", "200"));
        assertThrows(IllegalArgumentException.class, () -> light.validateState("unknown_key", "val"));
    }

    @Test
    @DisplayName("SimulatedThermostat validates temperatures and modes")
    void testThermostatValidation() {
        SimulatedThermostat thermostat = new SimulatedThermostat("therm-1", "Main Thermostat", "Hallway");
        assertEquals(DeviceType.THERMOSTAT, thermostat.getType());

        assertTrue(thermostat.isValidState("temperature", "22.5"));
        assertTrue(thermostat.isValidState("temperature", "-10.0"));
        assertFalse(thermostat.isValidState("temperature", "too_hot"));
        assertFalse(thermostat.isValidState("temperature", "150.0"));

        assertTrue(thermostat.isValidState("mode", "HEAT"));
        assertTrue(thermostat.isValidState("mode", "COOL"));
        assertTrue(thermostat.isValidState("mode", "AUTO"));
        assertFalse(thermostat.isValidState("mode", "BLAST"));
    }

    @Test
    @DisplayName("SimulatedDoorLock validates lock states")
    void testDoorLockValidation() {
        SimulatedDoorLock lock = new SimulatedDoorLock("lock-1", "Front Door Lock", "Entrance");
        assertTrue(lock.isValidState("status", "LOCKED"));
        assertTrue(lock.isValidState("status", "UNLOCKED"));
        assertTrue(lock.isValidState("lock", "LOCKED"));
        assertTrue(lock.isValidState("lock", "UNLOCKED"));
        assertFalse(lock.isValidState("status", "AJAR"));
    }

    @Test
    @DisplayName("SimulatedSmokeDetector validates smoke states and battery level")
    void testSmokeDetectorValidation() {
        SimulatedSmokeDetector detector = new SimulatedSmokeDetector("smoke-1", "Kitchen Smoke", "Kitchen");
        assertTrue(detector.isValidState("smoke", "CLEAR"));
        assertTrue(detector.isValidState("smoke", "DETECTED"));
        assertFalse(detector.isValidState("smoke", "BURNING"));

        assertTrue(detector.isValidState("battery", "100"));
        assertTrue(detector.isValidState("battery", "15"));
        assertFalse(detector.isValidState("battery", "-5"));
    }

    @Test
    @DisplayName("SimulatedMotionSensor validates motion states")
    void testMotionSensorValidation() {
        SimulatedMotionSensor motion = new SimulatedMotionSensor("motion-1", "Hall Motion", "Hallway");
        assertTrue(motion.isValidState("motion", "DETECTED"));
        assertTrue(motion.isValidState("motion", "NONE"));
        assertTrue(motion.isValidState("motion", "CLEAR"));
        assertFalse(motion.isValidState("motion", "MOVING_FAST"));
    }

    @Test
    @DisplayName("SimulatedWindow validates window open/closed status")
    void testWindowValidation() {
        SimulatedWindow window = new SimulatedWindow("win-1", "Bedroom Window", "Bedroom");
        assertTrue(window.isValidState("status", "OPEN"));
        assertTrue(window.isValidState("status", "CLOSED"));
        assertFalse(window.isValidState("status", "HALF_OPEN"));
    }

    @Test
    @DisplayName("SimulatedSmartPlug validates power and consumption")
    void testSmartPlugValidation() {
        SimulatedSmartPlug plug = new SimulatedSmartPlug("plug-1", "Heater Plug", "Living Room");
        assertTrue(plug.isValidState("power", "ON"));
        assertTrue(plug.isValidState("power", "OFF"));
        assertFalse(plug.isValidState("power", "STANDBY"));

        assertTrue(plug.isValidState("consumption", "1500.5"));
        assertTrue(plug.isValidState("consumption", "0.0"));
        assertFalse(plug.isValidState("consumption", "-10.0"));
    }

    @Test
    @DisplayName("SimulatedSiren validates alarm and volume")
    void testSirenValidation() {
        SimulatedSiren siren = new SimulatedSiren("siren-1", "Main Siren", "Hallway");
        assertTrue(siren.isValidState("alarm", "ON"));
        assertTrue(siren.isValidState("alarm", "OFF"));
        assertTrue(siren.isValidState("volume", "80"));
        assertFalse(siren.isValidState("volume", "120"));
    }

    @Test
    @DisplayName("SimulatedCamera validates status and motion")
    void testCameraValidation() {
        SimulatedCamera cam = new SimulatedCamera("cam-1", "Front Camera", "Entrance");
        assertTrue(cam.isValidState("status", "STREAMING"));
        assertTrue(cam.isValidState("status", "IDLE"));
        assertTrue(cam.isValidState("motion", "DETECTED"));
        assertFalse(cam.isValidState("status", "BROKEN"));
    }

    @Test
    @DisplayName("SimulatedDeviceFactory creates appropriate implementations for all DeviceTypes")
    void testDeviceFactory() {
        for (DeviceType type : DeviceType.values()) {
            Device d = new Device("dev-" + type.name(), "Test " + type.name(), type, "Room A");
            SimulatedDevice sim = SimulatedDeviceFactory.create(d);
            assertNotNull(sim);
            assertEquals(type, sim.getType());
            assertEquals("dev-" + type.name(), sim.getId());
            assertNotNull(sim.getDefaultState());
        }
    }

    @Test
    @DisplayName("toDevice correctly maps metadata and currentState")
    void testToDevice() {
        SimulatedLight light = new SimulatedLight("light-10", "Desk Light", "Office");
        Device device = light.toDevice(Map.of("power", "ON", "brightness", "85"));

        assertEquals("light-10", device.getId());
        assertEquals("Desk Light", device.getName());
        assertEquals(DeviceType.LIGHT, device.getType());
        assertEquals("Office", device.getRoom());
        assertEquals("ON", device.getStateValue("power"));
        assertEquals("85", device.getStateValue("brightness"));
    }
}

