package com.smarthome.ui;

import com.smarthome.domain.Action;
import com.smarthome.domain.Condition;
import com.smarthome.domain.ConditionGroup;
import com.smarthome.domain.Device;
import com.smarthome.domain.LeafCondition;
import com.smarthome.domain.LogicalOperator;
import com.smarthome.domain.Operator;
import com.smarthome.domain.PriorityTier;
import com.smarthome.domain.Rule;
import com.smarthome.exception.LogicalConflictException;
import com.smarthome.service.DeviceControlService;
import com.smarthome.service.RuleValidationService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Rule Builder view providing visual forms, TreeView-based condition nesting,
 * action authoring, and asynchronous cycle validation with conflict alerts.
 */
public class RuleBuilderView extends ScrollPane {

    private static final Logger logger = LoggerFactory.getLogger(RuleBuilderView.class);

    private final RuleValidationService ruleValidationService;
    private final DeviceControlService deviceControlService;
    private Consumer<Rule> onRuleSavedListener;

    private String editingRuleId;
    private Instant editingCreatedAt;

    private final TextField nameField = new TextField();
    private final ComboBox<PriorityTier> tierCombo = new ComboBox<>(FXCollections.observableArrayList(PriorityTier.values()));
    private final Spinner<Integer> tieBreakSpinner = new Spinner<>(0, 100, 0);
    private final CheckBox enabledCheckBox = new CheckBox("Rule Enabled");

    private final TreeView<ConditionTreeNode> conditionTreeView = new TreeView<>();
    private TreeItem<ConditionTreeNode> rootTreeItem;

    private final VBox nodeEditorBox = new VBox(8);
    private final ComboBox<LogicalOperator> groupOpCombo = new ComboBox<>(FXCollections.observableArrayList(LogicalOperator.values()));
    private final ComboBox<String> leafDeviceCombo = new ComboBox<>();
    private final TextField leafKeyField = new TextField();
    private final ComboBox<Operator> leafOperatorCombo = new ComboBox<>(FXCollections.observableArrayList(Operator.values()));
    private final TextField leafValueField = new TextField();

    private ConditionTreeNode activeNode;
    private boolean updatingEditor = false;

    private final ObservableList<Action> actionList = FXCollections.observableArrayList();
    private final TableView<Action> actionTable = new TableView<>(actionList);
    private final ComboBox<String> actionDeviceCombo = new ComboBox<>();
    private final TextField actionKeyField = new TextField();
    private final TextField actionValueField = new TextField();

    private final Button saveButton = new Button("Validate & Save Rule");
    private final Button clearButton = new Button("Clear Form");
    private final ProgressIndicator progressIndicator = new ProgressIndicator();
    private final Label statusMessage = new Label();

    public RuleBuilderView(RuleValidationService ruleValidationService, DeviceControlService deviceControlService) {
        this.ruleValidationService = Objects.requireNonNull(ruleValidationService, "ruleValidationService cannot be null");
        this.deviceControlService = Objects.requireNonNull(deviceControlService, "deviceControlService cannot be null");

        setFitToWidth(true);
        getStyleClass().add("rule-builder-scroll-pane");

        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("rule-builder-content");

        buildHeader(content);
        buildBasicDetailsForm(content);
        buildConditionSection(content);
        buildActionSection(content);
        buildFooter(content);

        setContent(content);
        refreshDeviceChoices();
        resetForm();
    }

    public void setOnRuleSavedListener(Consumer<Rule> listener) {
        this.onRuleSavedListener = listener;
    }

    public void refreshDeviceChoices() {
        List<String> deviceIds = deviceControlService.getAllDevices().stream()
                .map(Device::getId)
                .toList();

        leafDeviceCombo.setItems(FXCollections.observableArrayList(deviceIds));
        actionDeviceCombo.setItems(FXCollections.observableArrayList(deviceIds));
    }

    private void buildHeader(VBox container) {
        Label title = new Label("Rule Builder");
        title.getStyleClass().add("section-title");

        Label subtitle = new Label("Define automation rules with hierarchical condition trees, priority tiers, and actuations.");
        subtitle.getStyleClass().add("section-subtitle");

        container.getChildren().addAll(title, subtitle);
    }

    private void buildBasicDetailsForm(VBox container) {
        VBox card = new VBox(10);
        card.getStyleClass().add("form-card");

        Label cardHeader = new Label("1. Rule Information");
        cardHeader.getStyleClass().add("card-header-label");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        nameField.setPromptText("e.g. Smoke Emergency Ventilation");
        nameField.setPrefWidth(260);

        tierCombo.setValue(PriorityTier.COMFORT);
        tieBreakSpinner.setEditable(true);
        enabledCheckBox.setSelected(true);

        grid.add(new Label("Rule Name:"), 0, 0);
        grid.add(nameField, 1, 0);

        grid.add(new Label("Priority Tier:"), 0, 1);
        grid.add(tierCombo, 1, 1);

        grid.add(new Label("Tie-Break Order:"), 2, 1);
        grid.add(tieBreakSpinner, 3, 1);

        grid.add(new Label("Status:"), 0, 2);
        grid.add(enabledCheckBox, 1, 2);

        card.getChildren().addAll(cardHeader, grid);
        container.getChildren().add(card);
    }

    private void registerNodeEditorListeners() {
        groupOpCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (!updatingEditor && newV != null && activeNode != null && activeNode.isGroup()) {
                activeNode.setLogicalOperator(newV);
                conditionTreeView.refresh();
            }
        });

        leafDeviceCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (!updatingEditor && newV != null && activeNode != null && activeNode.isLeaf()) {
                activeNode.setDeviceId(newV);
                conditionTreeView.refresh();
            }
        });

        leafKeyField.textProperty().addListener((obs, oldV, newV) -> {
            if (!updatingEditor && activeNode != null && activeNode.isLeaf()) {
                activeNode.setStateKey(newV);
                conditionTreeView.refresh();
            }
        });

        leafOperatorCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (!updatingEditor && newV != null && activeNode != null && activeNode.isLeaf()) {
                activeNode.setOperator(newV);
                conditionTreeView.refresh();
            }
        });

        leafValueField.textProperty().addListener((obs, oldV, newV) -> {
            if (!updatingEditor && activeNode != null && activeNode.isLeaf()) {
                activeNode.setCompareValue(newV);
                conditionTreeView.refresh();
            }
        });
    }

    private void buildConditionSection(VBox container) {
        VBox card = new VBox(10);
        card.getStyleClass().add("form-card");

        Label cardHeader = new Label("2. Hierarchical Conditions (TreeView)");
        cardHeader.getStyleClass().add("card-header-label");

        HBox split = new HBox(14);

        // Left: Condition Tree & Toolbar
        VBox treeContainer = new VBox(8);
        HBox.setHgrow(treeContainer, Priority.ALWAYS);

        rootTreeItem = new TreeItem<>(ConditionTreeNode.createGroup(LogicalOperator.AND));
        rootTreeItem.setExpanded(true);
        conditionTreeView.setRoot(rootTreeItem);
        conditionTreeView.setPrefHeight(200);

        HBox treeButtons = new HBox(8);
        Button addLeafBtn = new Button("+ Add Condition Leaf");
        addLeafBtn.getStyleClass().add("btn-neutral");
        addLeafBtn.setOnAction(e -> addLeafToSelected());

        Button addGroupBtn = new Button("+ Add Group");
        addGroupBtn.getStyleClass().add("btn-neutral");
        addGroupBtn.setOnAction(e -> addGroupToSelected());

        Button removeNodeBtn = new Button("- Remove");
        removeNodeBtn.getStyleClass().add("btn-danger");
        removeNodeBtn.setOnAction(e -> removeSelectedNode());

        treeButtons.getChildren().addAll(addLeafBtn, addGroupBtn, removeNodeBtn);
        treeContainer.getChildren().addAll(conditionTreeView, treeButtons);

        // Right: Node Property Editor
        nodeEditorBox.setPrefWidth(300);
        nodeEditorBox.getStyleClass().add("node-editor-box");
        nodeEditorBox.setPadding(new Insets(10));

        registerNodeEditorListeners();

        conditionTreeView.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            updateNodeEditor(newItem != null ? newItem.getValue() : null);
        });

        split.getChildren().addAll(treeContainer, nodeEditorBox);
        card.getChildren().addAll(cardHeader, split);
        container.getChildren().add(card);
    }

    private void updateNodeEditor(ConditionTreeNode node) {
        activeNode = node;
        nodeEditorBox.getChildren().clear();
        if (node == null) {
            Label placeholder = new Label("Select a condition group or leaf node in the tree to edit its properties.");
            placeholder.setWrapText(true);
            nodeEditorBox.getChildren().add(placeholder);
            return;
        }

        updatingEditor = true;
        try {
            if (node.isGroup()) {
                Label groupTitle = new Label("Edit Logical Group");
                groupTitle.getStyleClass().add("sub-card-title");

                groupOpCombo.setValue(node.getLogicalOperator());

                nodeEditorBox.getChildren().addAll(groupTitle, new Label("Logical Operator:"), groupOpCombo);
            } else {
                Label leafTitle = new Label("Edit Condition Leaf");
                leafTitle.getStyleClass().add("sub-card-title");

                leafDeviceCombo.setValue(node.getDeviceId());
                leafKeyField.setText(node.getStateKey());
                leafOperatorCombo.setValue(node.getOperator());
                leafValueField.setText(node.getCompareValue());

                nodeEditorBox.getChildren().addAll(
                        leafTitle,
                        new Label("Target Device:"), leafDeviceCombo,
                        new Label("State Key:"), leafKeyField,
                        new Label("Comparison Operator:"), leafOperatorCombo,
                        new Label("Compare Value:"), leafValueField
                );
            }
        } finally {
            updatingEditor = false;
        }
    }

    private void addLeafToSelected() {
        TreeItem<ConditionTreeNode> selected = conditionTreeView.getSelectionModel().getSelectedItem();
        if (selected == null) selected = rootTreeItem;

        TreeItem<ConditionTreeNode> targetGroup = selected.getValue().isGroup() ? selected : selected.getParent();
        if (targetGroup == null) targetGroup = rootTreeItem;

        String dev = leafDeviceCombo.getItems().isEmpty() ? "device-1" : leafDeviceCombo.getItems().get(0);
        TreeItem<ConditionTreeNode> newLeaf = new TreeItem<>(
                ConditionTreeNode.createLeaf(dev, "power", Operator.EQ, "ON")
        );
        targetGroup.getChildren().add(newLeaf);
        targetGroup.setExpanded(true);
        conditionTreeView.getSelectionModel().select(newLeaf);
    }

    private void addGroupToSelected() {
        TreeItem<ConditionTreeNode> selected = conditionTreeView.getSelectionModel().getSelectedItem();
        if (selected == null) selected = rootTreeItem;

        TreeItem<ConditionTreeNode> targetGroup = selected.getValue().isGroup() ? selected : selected.getParent();
        if (targetGroup == null) targetGroup = rootTreeItem;

        TreeItem<ConditionTreeNode> newGroup = new TreeItem<>(
                ConditionTreeNode.createGroup(LogicalOperator.AND)
        );
        targetGroup.getChildren().add(newGroup);
        targetGroup.setExpanded(true);
        conditionTreeView.getSelectionModel().select(newGroup);
    }

    private void removeSelectedNode() {
        TreeItem<ConditionTreeNode> selected = conditionTreeView.getSelectionModel().getSelectedItem();
        if (selected != null && selected != rootTreeItem && selected.getParent() != null) {
            selected.getParent().getChildren().remove(selected);
        }
    }

    private void buildActionSection(VBox container) {
        VBox card = new VBox(10);
        card.getStyleClass().add("form-card");

        Label cardHeader = new Label("3. Actuations (Actions)");
        cardHeader.getStyleClass().add("card-header-label");

        // Action Table setup
        TableColumn<Action, String> devCol = new TableColumn<>("Device ID");
        devCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().deviceId()));
        devCol.setPrefWidth(180);

        TableColumn<Action, String> keyCol = new TableColumn<>("State Key");
        keyCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().stateKey()));
        keyCol.setPrefWidth(140);

        TableColumn<Action, String> valCol = new TableColumn<>("Target Value");
        valCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().targetValue()));
        valCol.setPrefWidth(140);

        actionTable.getColumns().add(devCol);
        actionTable.getColumns().add(keyCol);
        actionTable.getColumns().add(valCol);
        actionTable.setPrefHeight(150);

        // Action input controls
        HBox inputRow = new HBox(8);
        inputRow.setAlignment(Pos.CENTER_LEFT);

        actionKeyField.setPromptText("Key (e.g. power)");
        actionKeyField.setPrefWidth(120);

        actionValueField.setPromptText("Value (e.g. ON)");
        actionValueField.setPrefWidth(120);

        Button addActionBtn = new Button("Add Action");
        addActionBtn.getStyleClass().add("btn-neutral");
        addActionBtn.setOnAction(e -> {
            String dev = actionDeviceCombo.getValue();
            String key = actionKeyField.getText();
            String val = actionValueField.getText();

            if (dev != null && !dev.isBlank() && key != null && !key.isBlank() && val != null && !val.isBlank()) {
                actionList.add(new Action(dev.trim(), key.trim(), val.trim()));
                actionKeyField.clear();
                actionValueField.clear();
            }
        });

        Button removeActionBtn = new Button("Remove Selected");
        removeActionBtn.getStyleClass().add("btn-danger");
        removeActionBtn.setOnAction(e -> {
            Action selected = actionTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                actionList.remove(selected);
            }
        });

        inputRow.getChildren().addAll(
                new Label("Device:"), actionDeviceCombo,
                new Label("Key:"), actionKeyField,
                new Label("Value:"), actionValueField,
                addActionBtn, removeActionBtn
        );

        card.getChildren().addAll(cardHeader, actionTable, inputRow);
        container.getChildren().add(card);
    }

    private void buildFooter(VBox container) {
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(10, 0, 10, 0));

        saveButton.getStyleClass().add("btn-primary");
        saveButton.setOnAction(e -> onSaveClicked());

        clearButton.getStyleClass().add("btn-neutral");
        clearButton.setOnAction(e -> resetForm());

        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(24, 24);

        statusMessage.getStyleClass().add("status-message");

        footer.getChildren().addAll(saveButton, clearButton, progressIndicator, statusMessage);
        container.getChildren().add(footer);
    }

    private void onSaveClicked() {
        String name = nameField.getText() != null ? nameField.getText().trim() : "";
        if (name.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please provide a valid rule name.");
            return;
        }

        if (actionList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "A rule must have at least one action defined.");
            return;
        }

        Condition rootCondition = buildConditionFromTree(rootTreeItem);
        if (rootCondition == null) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please specify at least one valid condition in the tree.");
            return;
        }

        String ruleId = editingRuleId != null ? editingRuleId : UUID.randomUUID().toString();
        Instant createdAt = editingCreatedAt != null ? editingCreatedAt : Instant.now();

        Rule candidateRule = new Rule(
                ruleId,
                name,
                enabledCheckBox.isSelected(),
                tierCombo.getValue(),
                tieBreakSpinner.getValue(),
                rootCondition,
                new ArrayList<>(actionList),
                createdAt
        );

        // Execute save and validation in background Task
        statusMessage.setText("Validating DAG acyclicity and saving...");
        progressIndicator.setVisible(true);
        saveButton.setDisable(true);

        Task<Rule> saveTask = new Task<>() {
            @Override
            protected Rule call() throws Exception {
                return ruleValidationService.validateAndSave(candidateRule);
            }
        };

        saveTask.setOnSucceeded(event -> {
            Platform.runLater(() -> {
                progressIndicator.setVisible(false);
                saveButton.setDisable(false);
                statusMessage.setText("Rule saved successfully!");

                Rule saved = saveTask.getValue();
                showAlert(Alert.AlertType.INFORMATION, "Success",
                        String.format("Rule '%s' (ID: %s) validated and saved successfully.", saved.getName(), saved.getId()));

                if (onRuleSavedListener != null) {
                    onRuleSavedListener.accept(saved);
                }
                resetForm();
            });
        });

        saveTask.setOnFailed(event -> {
            Platform.runLater(() -> {
                progressIndicator.setVisible(false);
                saveButton.setDisable(false);
                statusMessage.setText("Save failed.");

                Throwable error = saveTask.getException();
                if (error instanceof LogicalConflictException lce) {
                    List<String> cyclePath = lce.getCyclePath();
                    String cycleStr = String.join(" -> ", cyclePath);
                    logger.warn("Circular dependency blocked by UI: {}", cycleStr);

                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Circular Dependency Conflict");
                    alert.setHeaderText("Rule Cannot Be Saved (Cycle Detected)");
                    alert.setContentText(String.format(
                            "This rule introduces a circular actuation loop across active rules.\n\n"
                                    + "Detected Cycle Path:\n%s\n\n"
                                    + "No database modifications occurred. Please adjust rule read/write keys to break the cycle.",
                            cycleStr
                    ));
                    alert.showAndWait();
                } else {
                    logger.error("Failed to save rule: {}", error.getMessage(), error);
                    showAlert(Alert.AlertType.ERROR, "Save Failed", "An error occurred while saving rule: " + error.getMessage());
                }
            });
        });

        new Thread(saveTask, "rule-save-worker").start();
    }

    private Condition buildConditionFromTree(TreeItem<ConditionTreeNode> item) {
        if (item == null || item.getValue() == null) return null;
        ConditionTreeNode node = item.getValue();

        if (node.isLeaf()) {
            if (node.getDeviceId() == null || node.getDeviceId().isBlank()) return null;
            return new LeafCondition(
                    node.getDeviceId(),
                    node.getStateKey(),
                    node.getOperator(),
                    node.getCompareValue()
            );
        } else {
            List<Condition> children = new ArrayList<>();
            for (TreeItem<ConditionTreeNode> child : item.getChildren()) {
                Condition c = buildConditionFromTree(child);
                if (c != null) {
                    children.add(c);
                }
            }
            if (children.isEmpty()) {
                return null;
            }
            return new ConditionGroup(node.getLogicalOperator(), children);
        }
    }

    public void loadRule(Rule rule) {
        if (rule == null) return;
        this.editingRuleId = rule.getId();
        this.editingCreatedAt = rule.getCreatedAt();

        nameField.setText(rule.getName());
        tierCombo.setValue(rule.getTier());
        tieBreakSpinner.getValueFactory().setValue(rule.getTieBreakOrder());
        enabledCheckBox.setSelected(rule.isEnabled());

        // Load Condition Tree
        rootTreeItem.getChildren().clear();
        populateTreeItemFromCondition(rootTreeItem, rule.getRootCondition());

        // Load Actions
        actionList.clear();
        actionList.addAll(rule.getActions());

        statusMessage.setText("Editing Rule: " + rule.getId());
    }

    private void populateTreeItemFromCondition(TreeItem<ConditionTreeNode> parentItem, Condition condition) {
        if (condition instanceof LeafCondition leaf) {
            parentItem.getChildren().add(new TreeItem<>(
                    ConditionTreeNode.createLeaf(leaf.deviceId(), leaf.stateKey(), leaf.operator(), leaf.value())
            ));
        } else if (condition instanceof ConditionGroup group) {
            TreeItem<ConditionTreeNode> groupItem = parentItem;
            if (parentItem != rootTreeItem || !parentItem.getValue().getLogicalOperator().equals(group.operator())) {
                groupItem = new TreeItem<>(ConditionTreeNode.createGroup(group.operator()));
                parentItem.getChildren().add(groupItem);
            } else {
                parentItem.getValue().setLogicalOperator(group.operator());
            }
            for (Condition child : group.children()) {
                populateTreeItemFromCondition(groupItem, child);
            }
            groupItem.setExpanded(true);
        }
    }

    public void resetForm() {
        this.editingRuleId = null;
        this.editingCreatedAt = null;

        nameField.clear();
        tierCombo.setValue(PriorityTier.COMFORT);
        tieBreakSpinner.getValueFactory().setValue(0);
        enabledCheckBox.setSelected(true);

        rootTreeItem.getChildren().clear();
        rootTreeItem.setValue(ConditionTreeNode.createGroup(LogicalOperator.AND));
        addLeafToSelected();

        actionList.clear();
        statusMessage.setText("");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}