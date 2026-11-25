package com.smarthome.ui;

import com.smarthome.service.DeviceControlService;
import com.smarthome.ui.viewmodel.DeviceCardViewModel;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * Dashboard Tab view presenting a grid of device cards showing live telemetry and controls.
 */
public class DashboardView extends VBox {

    private final ObservableList<DeviceCardViewModel> deviceViewModels;
    private final DeviceControlService controlService;
    private final FlowPane cardsGrid;
    private final FilteredList<DeviceCardViewModel> filteredDevices;
    private final Runnable applyFilter;

    public DashboardView(ObservableList<DeviceCardViewModel> deviceViewModels, DeviceControlService controlService) {
        this.deviceViewModels = Objects.requireNonNull(deviceViewModels, "deviceViewModels cannot be null");
        this.controlService = Objects.requireNonNull(controlService, "controlService cannot be null");

        setSpacing(14);
        setPadding(new Insets(16));
        getStyleClass().add("dashboard-view");

        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        Label searchLabel = new Label("Filter Devices:");
        searchLabel.getStyleClass().add("filter-label");

        TextField searchField = new TextField();
        searchField.setPromptText("Search by name, room, or ID...");
        searchField.setPrefWidth(260);

        ComboBox<String> roomFilter = new ComboBox<>();
        roomFilter.getItems().add("All Rooms");
        roomFilter.setValue("All Rooms");

        filterBar.getChildren().addAll(searchLabel, searchField, roomFilter);

        cardsGrid = new FlowPane();
        cardsGrid.setHgap(14);
        cardsGrid.setVgap(14);
        cardsGrid.setPadding(new Insets(4));
        cardsGrid.setAlignment(Pos.TOP_LEFT);

        this.filteredDevices = new FilteredList<>(this.deviceViewModels, d -> true);

        this.applyFilter = () -> {
            String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
            String selectedRoom = roomFilter.getValue();

            filteredDevices.setPredicate(card -> {
                String room = card.getRoom();
                boolean matchesRoom = "All Rooms".equals(selectedRoom)
                        || (room != null && room.equalsIgnoreCase(selectedRoom));

                String name = card.getName();
                String id = card.getId();
                boolean matchesSearch = query.isEmpty()
                        || (name != null && name.toLowerCase().contains(query))
                        || (id != null && id.toLowerCase().contains(query))
                        || (room != null && room.toLowerCase().contains(query));

                return matchesRoom && matchesSearch;
            });
            refreshGrid(filteredDevices);
        };

        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter.run());
        roomFilter.valueProperty().addListener((obs, oldV, newV) -> applyFilter.run());

        // Update room dropdown choices dynamically and re-apply active filter
        this.deviceViewModels.addListener((ListChangeListener<DeviceCardViewModel>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (DeviceCardViewModel vm : c.getAddedSubList()) {
                        addRoomIfMissing(roomFilter, vm.getRoom());
                    }
                }
            }
            applyFilter.run();
        });

        ScrollPane scrollPane = new ScrollPane(cardsGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("cards-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(filterBar, scrollPane);

        // Initial population
        for (DeviceCardViewModel vm : this.deviceViewModels) {
            addRoomIfMissing(roomFilter, vm.getRoom());
        }
        applyFilter.run();
    }

    public void refreshCards() {
        Platform.runLater(applyFilter);
    }

    private void addRoomIfMissing(ComboBox<String> roomFilter, String room) {
        if (room != null && !room.isBlank() && !roomFilter.getItems().contains(room)) {
            roomFilter.getItems().add(room);
        }
    }

    private void refreshGrid(FilteredList<DeviceCardViewModel> list) {
        cardsGrid.getChildren().clear();
        for (DeviceCardViewModel vm : list) {
            cardsGrid.getChildren().add(new DeviceCard(vm, controlService));
        }
    }
}