package com.smarthome.ui;

import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import com.smarthome.exception.LogicalConflictException;
import com.smarthome.service.RuleValidationService;
import com.smarthome.ui.viewmodel.RuleItemViewModel;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Rules management tab view providing a TableView of all configured rules with enable/disable,
 * edit, delete, and refresh controls.
 */
public class RulesView extends VBox {

    private static final Logger logger = LoggerFactory.getLogger(RulesView.class);

    private final ObservableList<RuleItemViewModel> ruleViewModels;
    private final RuleValidationService ruleValidationService;
    private final TableView<RuleItemViewModel> tableView = new TableView<>();

    private Consumer<Rule> onEditRuleListener;
    private Runnable onNewRuleListener;
    private final ProgressIndicator progressIndicator = new ProgressIndicator();

    public RulesView(ObservableList<RuleItemViewModel> ruleViewModels, RuleValidationService ruleValidationService) {
        this.ruleViewModels = Objects.requireNonNull(ruleViewModels, "ruleViewModels cannot be null");
        this.ruleValidationService = Objects.requireNonNull(ruleValidationService, "ruleValidationService cannot be null");

        setSpacing(12);
        setPadding(new Insets(16));
        getStyleClass().add("rules-view");

        buildHeader();
        buildTableView();
        buildToolbar();
    }

    public void setOnEditRuleListener(Consumer<Rule> listener) {
        this.onEditRuleListener = listener;
    }

    public void setOnNewRuleListener(Runnable listener) {
        this.onNewRuleListener = listener;
    }

    private void buildHeader() {
        Label title = new Label("Configured Rules");
        title.getStyleClass().add("section-title");

        Label subtitle = new Label("Manage active automation rules, inspect read/write sets, and toggle activation.");
        subtitle.getStyleClass().add("section-subtitle");

        getChildren().addAll(title, subtitle);
    }

    @SuppressWarnings("unchecked")
    private void buildTableView() {
        tableView.setItems(ruleViewModels);
        tableView.getStyleClass().add("rules-table");
        VBox.setVgrow(tableView, Priority.ALWAYS);

        // Name Column
        TableColumn<RuleItemViewModel, String> nameCol = new TableColumn<>("Rule Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        nameCol.setPrefWidth(220);

        // Tier Column
        TableColumn<RuleItemViewModel, PriorityTier> tierCol = new TableColumn<>("Tier");
        tierCol.setCellValueFactory(data -> data.getValue().tierProperty());
        tierCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(PriorityTier item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item.name());
                    badge.getStyleClass().addAll("tier-badge", "tier-" + item.name().toLowerCase());
                    setGraphic(badge);
                    setText(null);
                }
            }
        });
        tierCol.setPrefWidth(110);

        // Tie-Break Order Column
        TableColumn<RuleItemViewModel, Number> orderCol = new TableColumn<>("Tie-Break");
        orderCol.setCellValueFactory(data -> data.getValue().tieBreakOrderProperty());
        orderCol.setPrefWidth(80);

        // Enabled Column
        TableColumn<RuleItemViewModel, Boolean> enabledCol = new TableColumn<>("Status");
        enabledCol.setCellValueFactory(data -> data.getValue().enabledProperty());
        enabledCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label statusBadge = new Label(item ? "ENABLED" : "DISABLED");
                    statusBadge.getStyleClass().addAll("status-badge", item ? "status-enabled" : "status-disabled");
                    setGraphic(statusBadge);
                    setText(null);
                }
            }
        });
        enabledCol.setPrefWidth(100);

        // Read Set Column
        TableColumn<RuleItemViewModel, String> readSetCol = new TableColumn<>("Condition Read Set");
        readSetCol.setCellValueFactory(data -> data.getValue().readSetSummaryProperty());
        readSetCol.setPrefWidth(160);

        // Write Set Column
        TableColumn<RuleItemViewModel, String> writeSetCol = new TableColumn<>("Action Write Set");
        writeSetCol.setCellValueFactory(data -> data.getValue().writeSetSummaryProperty());
        writeSetCol.setPrefWidth(160);

        // Last Fired Column
        TableColumn<RuleItemViewModel, String> lastFiredCol = new TableColumn<>("Last Fired");
        lastFiredCol.setCellValueFactory(data -> data.getValue().lastFiredProperty());
        lastFiredCol.setPrefWidth(110);

        tableView.getColumns().addAll(nameCol, tierCol, orderCol, enabledCol, readSetCol, writeSetCol, lastFiredCol);
        getChildren().add(tableView);
    }

    private void buildToolbar() {
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button newBtn = new Button("+ New Rule");
        newBtn.getStyleClass().add("btn-primary");
        newBtn.setOnAction(e -> {
            if (onNewRuleListener != null) {
                onNewRuleListener.run();
            }
        });

        Button toggleEnableBtn = new Button("Enable / Disable");
        toggleEnableBtn.getStyleClass().add("btn-neutral");
        toggleEnableBtn.setOnAction(e -> onToggleEnableClicked());

        Button editBtn = new Button("Edit Rule");
        editBtn.getStyleClass().add("btn-neutral");
        editBtn.setOnAction(e -> {
            RuleItemViewModel selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null && onEditRuleListener != null) {
                onEditRuleListener.accept(selected.getRule());
            }
        });

        Button deleteBtn = new Button("Delete Rule");
        deleteBtn.getStyleClass().add("btn-danger");
        deleteBtn.setOnAction(e -> onDeleteClicked());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("btn-neutral");
        refreshBtn.setOnAction(e -> refreshRulesFromService());

        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(20, 20);

        toolbar.getChildren().addAll(newBtn, editBtn, toggleEnableBtn, deleteBtn, refreshBtn, progressIndicator);
        getChildren().add(toolbar);
    }

    private void onToggleEnableClicked() {
        RuleItemViewModel selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", "Please select a rule from the table to toggle its status.");
            return;
        }

        String ruleId = selected.getId();
        boolean currentlyEnabled = selected.isEnabled();
        progressIndicator.setVisible(true);

        Task<Rule> task = new Task<>() {
            @Override
            protected Rule call() throws Exception {
                if (currentlyEnabled) {
                    return ruleValidationService.disableRule(ruleId);
                } else {
                    return ruleValidationService.enableRule(ruleId);
                }
            }
        };

        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                progressIndicator.setVisible(false);
                selected.setRule(task.getValue());
                tableView.refresh();
            });
        });

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                progressIndicator.setVisible(false);
                Throwable err = task.getException();
                if (err instanceof LogicalConflictException lce) {
                    showAlert(Alert.AlertType.ERROR, "Cannot Enable Rule",
                            "Enabling this rule introduces a circular dependency:\n" + String.join(" -> ", lce.getCyclePath()));
                } else {
                    showAlert(Alert.AlertType.ERROR, "Operation Failed", "Could not toggle rule: " + err.getMessage());
                }
            });
        });

        new Thread(task, "rule-toggle-worker").start();
    }

    private void onDeleteClicked() {
        RuleItemViewModel selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.INFORMATION, "No Selection", "Please select a rule to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText("Delete Rule '" + selected.getName() + "'?");
        confirm.setContentText("This will permanently remove the rule and its conditions from the database.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            progressIndicator.setVisible(true);
            Task<Boolean> deleteTask = new Task<>() {
                @Override
                protected Boolean call() {
                    return ruleValidationService.deleteRule(selected.getId());
                }
            };

            deleteTask.setOnSucceeded(e -> {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    ruleViewModels.remove(selected);
                });
            });

            deleteTask.setOnFailed(e -> {
                Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    showAlert(Alert.AlertType.ERROR, "Delete Failed", "Could not delete rule: " + deleteTask.getException().getMessage());
                });
            });

            new Thread(deleteTask, "rule-delete-worker").start();
        }
    }

    public void refreshRulesFromService() {
        progressIndicator.setVisible(true);
        Task<List<Rule>> loadTask = new Task<>() {
            @Override
            protected List<Rule> call() {
                return ruleValidationService.findAllRules();
            }
        };

        loadTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                progressIndicator.setVisible(false);
                ruleViewModels.clear();
                for (Rule r : loadTask.getValue()) {
                    ruleViewModels.add(new RuleItemViewModel(r));
                }
            });
        });

        loadTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                progressIndicator.setVisible(false);
                logger.error("Failed to reload rules: {}", loadTask.getException().getMessage());
            });
        });

        new Thread(loadTask, "rule-load-worker").start();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}

