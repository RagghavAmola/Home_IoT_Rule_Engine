package com.smarthome.ui;

import com.smarthome.service.monitor.RuleMonitorService;
import com.smarthome.service.monitor.TickResult;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Collections;
import java.util.Objects;

/**
 * Root UI container assembling the TabPane navigation, header status bar, and footer controls.
 */
public class MainView extends BorderPane {

    private final MainPresenter presenter;
    private final TabPane tabPane = new TabPane();

    private final DashboardView dashboardView;
    private final RuleBuilderView ruleBuilderView;
    private final RulesView rulesView;
    private final ActivityLogView activityLogView;
    private final DependencyGraphView dependencyGraphView;

    private final Tab dashboardTab;
    private final Tab ruleBuilderTab;
    private final Tab rulesTab;
    private final Tab activityLogTab;
    private final Tab dependencyGraphTab;

    private final Label statusSummaryLabel = new Label("System Ready");

    public MainView(MainPresenter presenter) {
        this.presenter = Objects.requireNonNull(presenter, "presenter cannot be null");
        getStyleClass().add("main-view");

        this.dashboardView = new DashboardView(presenter.getDeviceViewModels(), presenter.getDeviceControlService());
        this.ruleBuilderView = new RuleBuilderView(presenter.getRuleValidationService(), presenter.getDeviceControlService());
        this.rulesView = new RulesView(presenter.getRuleViewModels(), presenter.getRuleValidationService());
        this.activityLogView = new ActivityLogView(presenter.getActivityLogs());
        this.dependencyGraphView = new DependencyGraphView(presenter.getRuleValidationService());

        this.dashboardTab = new Tab("Dashboard", dashboardView);
        this.dashboardTab.setClosable(false);

        this.ruleBuilderTab = new Tab("Rule Builder", ruleBuilderView);
        this.ruleBuilderTab.setClosable(false);

        this.rulesTab = new Tab("Rules", rulesView);
        this.rulesTab.setClosable(false);

        this.activityLogTab = new Tab("Activity Log", activityLogView);
        this.activityLogTab.setClosable(false);

        this.dependencyGraphTab = new Tab("Dependency Graph", dependencyGraphView);
        this.dependencyGraphTab.setClosable(false);

        tabPane.getTabs().addAll(dashboardTab, ruleBuilderTab, rulesTab, activityLogTab, dependencyGraphTab);

        setupTabInteractions();

        setTop(buildHeaderBar());
        setCenter(tabPane);
        setBottom(buildStatusBar());
    }

    private void setupTabInteractions() {
        rulesView.setOnNewRuleListener(() -> {
            ruleBuilderView.resetForm();
            ruleBuilderView.refreshDeviceChoices();
            tabPane.getSelectionModel().select(ruleBuilderTab);
        });

        rulesView.setOnEditRuleListener(rule -> {
            ruleBuilderView.loadRule(rule);
            ruleBuilderView.refreshDeviceChoices();
            tabPane.getSelectionModel().select(ruleBuilderTab);
        });

        ruleBuilderView.setOnRuleSavedListener(savedRule -> {
            presenter.onRuleSaved(savedRule);
            dependencyGraphView.renderGraph(Collections.emptyList());
        });

        dependencyGraphTab.setOnSelectionChanged(e -> {
            if (dependencyGraphTab.isSelected()) {
                dependencyGraphView.renderGraph(Collections.emptyList());
            }
        });
    }

    private HBox buildHeaderBar() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 20, 12, 20));
        header.getStyleClass().add("app-header");

        VBox titleBox = new VBox(2);
        Label appTitle = new Label("Smart Home IoT Rule Engine");
        appTitle.getStyleClass().add("app-title");

        Label appSub = new Label("Acyclic DAG Validation • Priority Conflict Resolution • Real-Time Monitor");
        appSub.getStyleClass().add("app-subtitle");

        titleBox.getChildren().addAll(appTitle, appSub);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_RIGHT);

        RuleMonitorService monitor = presenter.getRuleMonitorService();

        ToggleButton pauseToggle = new ToggleButton("Pause Monitor");
        pauseToggle.getStyleClass().add("btn-neutral");
        pauseToggle.setSelected(monitor.isPaused());
        pauseToggle.setOnAction(e -> {
            if (pauseToggle.isSelected()) {
                monitor.pause();
                pauseToggle.setText("Resume Monitor");
                statusSummaryLabel.setText("Monitor Paused");
            } else {
                monitor.resume();
                pauseToggle.setText("Pause Monitor");
                statusSummaryLabel.setText("Monitor Running");
            }
        });

        Button evalNowBtn = new Button("Evaluate Now");
        evalNowBtn.getStyleClass().add("btn-primary");
        evalNowBtn.setOnAction(e -> {
            new Thread(() -> {
                TickResult result = monitor.executeTick();
                Platform.runLater(() -> statusSummaryLabel.setText(
                        String.format("Tick executed: %d rules checked, %d actions applied, %d overrides",
                                result.rulesEvaluatedCount(), result.actionsApplied().size(), result.overrides().size())
                ));
            }, "manual-eval-worker").start();
        });

        controls.getChildren().addAll(pauseToggle, evalNowBtn);
        header.getChildren().addAll(titleBox, controls);

        return header;
    }

    private HBox buildStatusBar() {
        HBox statusBar = new HBox(16);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(8, 16, 8, 16));
        statusBar.getStyleClass().add("status-bar");

        Label intervalLabel = new Label("Monitor: " + presenter.getRuleMonitorService().getMonitorIntervalMs() + "ms");
        intervalLabel.getStyleClass().add("status-item");

        Label devicesCountLabel = new Label();
        devicesCountLabel.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> "Devices: " + presenter.getDeviceViewModels().size(), presenter.getDeviceViewModels()
        ));
        devicesCountLabel.getStyleClass().add("status-item");

        Label rulesCountLabel = new Label();
        rulesCountLabel.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> "Rules: " + presenter.getRuleViewModels().size(), presenter.getRuleViewModels()
        ));
        rulesCountLabel.getStyleClass().add("status-item");

        statusSummaryLabel.getStyleClass().add("status-summary");
        HBox.setHgrow(statusSummaryLabel, Priority.ALWAYS);

        statusBar.getChildren().addAll(intervalLabel, devicesCountLabel, rulesCountLabel, statusSummaryLabel);
        return statusBar;
    }
}

