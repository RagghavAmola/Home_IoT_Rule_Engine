package com.smarthome.ui.viewmodel;

import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceType;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.Map;
import java.util.Objects;

/**
 * ViewModel encapsulating a smart device for rendering interactive dashboard cards.
 */
public class DeviceCardViewModel {

    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<DeviceType> type = new SimpleObjectProperty<>();
    private final StringProperty room = new SimpleStringProperty();
    private final StringProperty primaryState = new SimpleStringProperty();
    private final ObservableMap<String, String> stateMap = FXCollections.observableHashMap();

    public DeviceCardViewModel(Device device, Map<String, String> currentStates) {
        Objects.requireNonNull(device, "device cannot be null");
        this.id.set(device.getId());
        this.name.set(device.getName());
        this.type.set(device.getType());
        this.room.set(device.getRoom());

        if (currentStates != null) {
            this.stateMap.putAll(currentStates);
        }
        applyPrimaryState();
    }

    public void updateState(String key, String value) {
        if (key != null && value != null) {
            Runnable task = () -> {
                stateMap.put(key, value);
                applyPrimaryState();
            };
            if (Platform.isFxApplicationThread()) {
                task.run();
            } else {
                try {
                    Platform.runLater(task);
                } catch (IllegalStateException e) {
                    task.run();
                }
            }
        }
    }

    public String getState(String key) {
        return stateMap.get(key);
    }

    private void applyPrimaryState() {
        DeviceType devType = type.get();
        if (devType == null) {
            return;
        }

        String primary = switch (devType) {
            case LIGHT, SMART_PLUG -> stateMap.getOrDefault("power", "OFF");
            case THERMOSTAT -> stateMap.getOrDefault("temperature", "20.0") + " °C";
            case DOOR_LOCK -> stateMap.getOrDefault("status", stateMap.getOrDefault("lock", "LOCKED"));
            case SMOKE_DETECTOR -> stateMap.getOrDefault("smoke", stateMap.getOrDefault("status", "CLEAR"));
            case MOTION_SENSOR -> stateMap.getOrDefault("motion", stateMap.getOrDefault("status", "NONE"));
            case WINDOW -> stateMap.getOrDefault("status", stateMap.getOrDefault("state", "CLOSED"));
            case SIREN -> stateMap.getOrDefault("alarm", stateMap.getOrDefault("power", "OFF"));
            case CAMERA -> stateMap.getOrDefault("status", "IDLE");
        };
        primaryState.set(primary);
    }

    public String getId() {
        return id.get();
    }

    public StringProperty idProperty() {
        return id;
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public DeviceType getType() {
        return type.get();
    }

    public ObjectProperty<DeviceType> typeProperty() {
        return type;
    }

    public String getRoom() {
        return room.get();
    }

    public StringProperty roomProperty() {
        return room;
    }

    public String getPrimaryState() {
        return primaryState.get();
    }

    public StringProperty primaryStateProperty() {
        return primaryState;
    }

    public ObservableMap<String, String> getStateMap() {
        return stateMap;
    }
}