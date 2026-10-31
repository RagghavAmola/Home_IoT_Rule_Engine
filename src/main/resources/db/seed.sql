-- Smart Home IoT Rule Engine Seed Data

-- Priority Tiers (SAFETY=0, COMFORT=1, ENERGY=2)
INSERT OR IGNORE INTO priority_tier (id, name, tier_rank) VALUES
('SAFETY', 'SAFETY', 0),
('COMFORT', 'COMFORT', 1),
('ENERGY', 'ENERGY', 2);

-- Device Types matching DeviceType enum
INSERT OR IGNORE INTO device_type (id, name) VALUES
('LIGHT', 'Light'),
('THERMOSTAT', 'Thermostat'),
('DOOR_LOCK', 'Door Lock'),
('SMOKE_DETECTOR', 'Smoke Detector'),
('MOTION_SENSOR', 'Motion Sensor'),
('WINDOW', 'Window'),
('SMART_PLUG', 'Smart Plug'),
('SIREN', 'Siren Alarm'),
('CAMERA', 'Camera');

-- Sample Devices
INSERT OR IGNORE INTO device (id, name, type_id, room) VALUES
('light-living-room', 'Living Room Ceiling Light', 'LIGHT', 'Living Room'),
('thermostat-living-room', 'Living Room Thermostat', 'THERMOSTAT', 'Living Room'),
('motion-sensor-hallway', 'Hallway Motion Sensor', 'MOTION_SENSOR', 'Hallway'),
('smoke-detector-kitchen', 'Kitchen Smoke Detector', 'SMOKE_DETECTOR', 'Kitchen'),
('door-lock-front', 'Front Door Lock', 'DOOR_LOCK', 'Entrance'),
('window-bedroom', 'Master Bedroom Window', 'WINDOW', 'Bedroom'),
('plug-heater', 'Space Heater Smart Plug', 'SMART_PLUG', 'Living Room'),
('siren-main', 'Central Siren', 'SIREN', 'Hallway');

-- Sample Initial Device States
INSERT OR IGNORE INTO device_state (device_id, state_key, state_value, updated_at) VALUES
('light-living-room', 'power', 'OFF', '2026-10-31T00:00:00Z'),
('light-living-room', 'brightness', '0', '2026-10-31T00:00:00Z'),
('thermostat-living-room', 'temperature', '21.0', '2026-10-31T00:00:00Z'),
('thermostat-living-room', 'target_temp', '22.0', '2026-10-31T00:00:00Z'),
('thermostat-living-room', 'mode', 'HEAT', '2026-10-31T00:00:00Z'),
('motion-sensor-hallway', 'motion', 'NONE', '2026-10-31T00:00:00Z'),
('smoke-detector-kitchen', 'smoke', 'CLEAR', '2026-10-31T00:00:00Z'),
('door-lock-front', 'status', 'LOCKED', '2026-10-31T00:00:00Z'),
('window-bedroom', 'status', 'CLOSED', '2026-10-31T00:00:00Z'),
('plug-heater', 'power', 'OFF', '2026-10-31T00:00:00Z'),
('siren-main', 'alarm', 'OFF', '2026-10-31T00:00:00Z');

