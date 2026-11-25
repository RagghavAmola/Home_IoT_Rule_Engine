package com.smarthome.ui;

import com.smarthome.ui.viewmodel.ActivityLogItemViewModel;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.Objects;

/**
 * Activity Log tab view displaying live real-time rule actuations, priority overrides,
 * and system telemetry audit records in ascending chronological order.
 */
public class ActivityLogView extends VBox {

    private final ObservableList<ActivityLogItemViewModel> logItems;
    private final SortedList<ActivityLogItemViewModel> sortedLogItems;
    private final TableView<ActivityLogItemViewModel> tableView = new TableView<>();
    private final CheckBox autoScrollCheck = new CheckBox("Auto-scroll to latest");

    public ActivityLogView(ObservableList<ActivityLogItemViewModel> logItems) {
        this.logItems = Objects.requireNonNull(logItems, "logItems cannot be null");

        // Maintain ascending chronological order by default while allowing column header sorting
        this.sortedLogItems = new SortedList<>(logItems);
        this.sortedLogItems.comparatorProperty().bind(Bindings.createObjectBinding(() -> {
            if (tableView.getComparator() != null) {
                return tableView.getComparator();
            }
            return Comparator.comparing(ActivityLogItemViewModel::getRawTimestamp)
                    .thenComparingLong(ActivityLogItemViewModel::getSequenceNumber);
        }, tableView.comparatorProperty()));

        setSpacing(12);
        setPadding(new Insets(16));
        getStyleClass().add("activity-log-view");

        buildHeader();
        buildTableView();
        buildFooter();
    }

    private void buildHeader() {
        Label title = new Label("Live Activity Log");
        title.getStyleClass().add("section-title");

        Label subtitle = new Label("Real-time stream of rule evaluation executions, priority overrides, and actuation events in chronological order.");
        subtitle.getStyleClass().add("section-subtitle");

        getChildren().addAll(title, subtitle);
    }

    @SuppressWarnings("unchecked")
    private void buildTableView() {
        tableView.setItems(sortedLogItems);
        tableView.getStyleClass().add("activity-table");
        VBox.setVgrow(tableView, Priority.ALWAYS);

        TableColumn<ActivityLogItemViewModel, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(data -> data.getValue().timestampProperty());
        timeCol.setComparator(String::compareTo);
        timeCol.setPrefWidth(120);

        TableColumn<ActivityLogItemViewModel, String> typeCol = new TableColumn<>("Event Type");
        typeCol.setCellValueFactory(data -> data.getValue().typeProperty());
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().addAll("log-badge", "log-" + item.toLowerCase());
                    setGraphic(badge);
                    setText(null);
                }
            }
        });
        typeCol.setPrefWidth(130);

        TableColumn<ActivityLogItemViewModel, String> summaryCol = new TableColumn<>("Summary");
        summaryCol.setCellValueFactory(data -> data.getValue().summaryProperty());
        summaryCol.setPrefWidth(450);

        TableColumn<ActivityLogItemViewModel, String> detailsCol = new TableColumn<>("Details");
        detailsCol.setCellValueFactory(data -> data.getValue().detailsProperty());
        detailsCol.setPrefWidth(220);

        tableView.getColumns().addAll(timeCol, typeCol, summaryCol, detailsCol);

        sortedLogItems.addListener((javafx.collections.ListChangeListener<ActivityLogItemViewModel>) c -> {
            if (autoScrollCheck.isSelected() && !sortedLogItems.isEmpty()) {
                Platform.runLater(() -> tableView.scrollTo(sortedLogItems.size() - 1));
            }
        });

        getChildren().add(tableView);
    }

    private void buildFooter() {
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_LEFT);

        autoScrollCheck.setSelected(true);

        Button clearBtn = new Button("Clear Log");
        clearBtn.getStyleClass().add("btn-neutral");
        clearBtn.setOnAction(e -> logItems.clear());

        Label countLabel = new Label();
        countLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "Total events logged: " + sortedLogItems.size(), sortedLogItems
        ));
        countLabel.getStyleClass().add("event-count-label");

        footer.getChildren().addAll(autoScrollCheck, clearBtn, countLabel);
        getChildren().add(footer);
    }
}
