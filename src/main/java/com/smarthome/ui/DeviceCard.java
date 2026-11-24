package com.smarthome.ui;

import com.smarthome.domain.DeviceType;
import com.smarthome.service.DeviceControlService;
import com.smarthome.ui.viewmodel.DeviceCardViewModel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.Objects;

/**
 * Interactive card component presenting an individual device's live telemetry and control inputs.
 */
public class DeviceCard extends VBox {

    private final DeviceCardViewModel viewModel;
    private final DeviceControlService controlService;

    public DeviceCard(DeviceCardViewModel viewModel, DeviceControlService controlService) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel cannot be null");
        this.controlService = Objects.requireNonNull(controlService, "controlService cannot be null");

        getStyleClass().add("device-card");
        setPadding(new Insets(14));
        setSpacing(10);
        setPrefWidth(260);
        setMaxWidth(300);

        buildCard();
    }

    private void buildCard() {
        // 1. Header: Name & Room
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(8);

        Label nameLabel = new Label();
        nameLabel.textProperty().bind(viewModel.nameProperty());
        nameLabel.getStyleClass().add("device-name");
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Label roomBadge = new Label();
        roomBadge.textProperty().bind(viewModel.roomProperty());
        roomBadge.getStyleClass().add("badge-room");

        header.getChildren().addAll(nameLabel, roomBadge);

        // 2. State Display
        HBox stateBox = new HBox();
        stateBox.setAlignment(Pos.CENTER_LEFT);
        stateBox.setSpacing(6);

        Label typeLabel = new Label(viewModel.getType().name());
        typeLabel.getStyleClass().add("device-type-label");

        Label stateValLabel = new Label();
        stateValLabel.textProperty().bind(viewModel.primaryStateProperty());
        stateValLabel.getStyleClass().add("device-state-value");

        stateBox.getChildren().addAll(typeLabel, stateValLabel);

        // 3. Interactive Controls per DeviceType
        VBox controlsBox = new VBox(6);
        controlsBox.setAlignment(Pos.CENTER_LEFT);
        populateControls(controlsBox);

        getChildren().addAll(header, stateBox, controlsBox);
    }

    private void applyState(String deviceId, String key, String value) {
        controlService.updateState(deviceId, key, value);
        viewModel.updateState(key, value);
    }

    private void populateControls(VBox container) {
        DeviceType type = viewModel.getType();
        String deviceId = viewModel.getId();

        switch (type) {
            case LIGHT -> {
                ToggleButton powerToggle = new ToggleButton("Power ON/OFF");
                powerToggle.setMaxWidth(Double.MAX_VALUE);
                powerToggle.getStyleClass().add("btn-toggle");
                powerToggle.setSelected("ON".equalsIgnoreCase(viewModel.getState("power")));
                viewModel.primaryStateProperty().addListener((obs, oldV, newV) ->
                        powerToggle.setSelected("ON".equalsIgnoreCase(viewModel.getState("power"))));

                powerToggle.setOnAction(e -> {
                    String target = powerToggle.isSelected() ? "ON" : "OFF";
                    applyState(deviceId, "power", target);
                });
                container.getChildren().add(powerToggle);
            }
            case THERMOSTAT -> {
                Label tempLabel = new Label("Set Temp: 21.0 °C");
                Slider tempSlider = new Slider(15.0, 30.0, 21.0);
                tempSlider.setShowTickLabels(true);
                tempSlider.setShowTickMarks(true);
                tempSlider.setMajorTickUnit(5.0);
                tempSlider.setBlockIncrement(0.5);

                final boolean[] syncing = {false};
                String currentTemp = viewModel.getState("temperature");
                if (currentTemp != null) {
                    try {
                        double val = Double.parseDouble(currentTemp);
                        syncing[0] = true;
                        tempSlider.setValue(val);
                        syncing[0] = false;
                        tempLabel.setText(String.format(Locale.ROOT, "Set Temp: %.1f °C", val));
                    } catch (NumberFormatException ignored) {}
                }

                tempSlider.valueProperty().addListener((obs, oldV, newV) -> {
                    double val = newV.doubleValue();
                    tempLabel.setText(String.format(Locale.ROOT, "Set Temp: %.1f °C", val));
                    if (!syncing[0] && !tempSlider.isValueChanging()) {
                        applyState(deviceId, "temperature", String.format(Locale.ROOT, "%.1f", val));
                    }
                });

                tempSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
                    if (!isChanging && !syncing[0]) {
                        applyState(deviceId, "temperature", String.format(Locale.ROOT, "%.1f", tempSlider.getValue()));
                    }
                });

                viewModel.primaryStateProperty().addListener((obs, oldV, newV) -> {
                    String latest = viewModel.getState("temperature");
                    if (latest != null) {
                        try {
                            double val = Double.parseDouble(latest);
                            if (Math.abs(tempSlider.getValue() - val) > 0.05) {
                                syncing[0] = true;
                                tempSlider.setValue(val);
                                syncing[0] = false;
                            }
                            tempLabel.setText(String.format(Locale.ROOT, "Set Temp: %.1f °C", val));
                        } catch (NumberFormatException ignored) {}
                    }
                });

                container.getChildren().addAll(tempLabel, tempSlider);
            }
            case DOOR_LOCK -> {
                Button toggleLock = new Button("Toggle Lock");
                toggleLock.setMaxWidth(Double.MAX_VALUE);
                toggleLock.getStyleClass().add("btn-action");
                toggleLock.setOnAction(e -> {
                    String current = viewModel.getState("status");
                    String next = "LOCKED".equalsIgnoreCase(current) ? "UNLOCKED" : "LOCKED";
                    applyState(deviceId, "status", next);
                });
                container.getChildren().add(toggleLock);
            }
            case SMOKE_DETECTOR -> {
                HBox smokeButtons = new HBox(6);
                Button triggerSmoke = new Button("Simulate Smoke");
                triggerSmoke.getStyleClass().add("btn-danger");
                triggerSmoke.setOnAction(e -> applyState(deviceId, "smoke", "DETECTED"));

                Button clearSmoke = new Button("Clear Smoke");
                clearSmoke.getStyleClass().add("btn-neutral");
                clearSmoke.setOnAction(e -> applyState(deviceId, "smoke", "CLEAR"));

                smokeButtons.getChildren().addAll(triggerSmoke, clearSmoke);
                container.getChildren().add(smokeButtons);
            }
            case MOTION_SENSOR -> {
                HBox motionButtons = new HBox(6);
                Button triggerMotion = new Button("Simulate Motion");
                triggerMotion.getStyleClass().add("btn-warning");
                triggerMotion.setOnAction(e -> applyState(deviceId, "motion", "DETECTED"));

                Button clearMotion = new Button("Clear");
                clearMotion.getStyleClass().add("btn-neutral");
                clearMotion.setOnAction(e -> applyState(deviceId, "motion", "NONE"));

                motionButtons.getChildren().addAll(triggerMotion, clearMotion);
                container.getChildren().add(motionButtons);
            }
            case WINDOW -> {
                Button toggleWin = new Button("Open / Close Window");
                toggleWin.setMaxWidth(Double.MAX_VALUE);
                toggleWin.getStyleClass().add("btn-action");
                toggleWin.setOnAction(e -> {
                    String current = viewModel.getState("status");
                    String next = "OPEN".equalsIgnoreCase(current) ? "CLOSED" : "OPEN";
                    applyState(deviceId, "status", next);
                });
                container.getChildren().add(toggleWin);
            }
            case SMART_PLUG -> {
                ToggleButton plugToggle = new ToggleButton("Plug Power");
                plugToggle.setMaxWidth(Double.MAX_VALUE);
                plugToggle.getStyleClass().add("btn-toggle");
                plugToggle.setSelected("ON".equalsIgnoreCase(viewModel.getState("power")));
                viewModel.primaryStateProperty().addListener((obs, oldV, newV) ->
                        plugToggle.setSelected("ON".equalsIgnoreCase(viewModel.getState("power"))));

                plugToggle.setOnAction(e -> {
                    String target = plugToggle.isSelected() ? "ON" : "OFF";
                    applyState(deviceId, "power", target);
                });
                container.getChildren().add(plugToggle);
            }
            case SIREN -> {
                Button toggleSiren = new Button("Alarm ON/OFF");
                toggleSiren.setMaxWidth(Double.MAX_VALUE);
                toggleSiren.getStyleClass().add("btn-danger");
                toggleSiren.setOnAction(e -> {
                    String current = viewModel.getState("alarm");
                    String next = "ON".equalsIgnoreCase(current) ? "OFF" : "ON";
                    applyState(deviceId, "alarm", next);
                });
                container.getChildren().add(toggleSiren);
            }
            case CAMERA -> {
                Button streamBtn = new Button("Toggle Stream");
                streamBtn.setMaxWidth(Double.MAX_VALUE);
                streamBtn.getStyleClass().add("btn-action");
                streamBtn.setOnAction(e -> {
                    String current = viewModel.getState("status");
                    String next = "STREAMING".equalsIgnoreCase(current) ? "IDLE" : "STREAMING";
                    applyState(deviceId, "status", next);
                });
                container.getChildren().add(streamBtn);
            }
        }
    }
}