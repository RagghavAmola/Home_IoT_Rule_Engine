package com.smarthome.ui;

import com.smarthome.domain.Action;
import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.domain.Rule;
import com.smarthome.service.DeviceControlService;
import com.smarthome.service.RuleValidationService;
import com.smarthome.service.monitor.EngineEventListener;
import com.smarthome.service.monitor.RuleMonitorService;
import com.smarthome.ui.viewmodel.ActivityLogItemViewModel;
import com.smarthome.ui.viewmodel.DeviceCardViewModel;
import com.smarthome.ui.viewmodel.RuleItemViewModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Main UI presenter coordinating view models, services, and event dispatch.
 * <p>
 * Implements {@link EngineEventListener} to observe background monitoring events.
 * All callbacks from background threads are strictly marshalled onto the JavaFX Application
 * Thread using {@link Platform#runLater(Runnable)}. Contains zero business logic.
 * </p>
 */
public class MainPresenter implements EngineEventListener {

    private static final Logger logger = LoggerFactory.getLogger(MainPresenter.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final DeviceControlService deviceControlService;
    private final RuleValidationService ruleValidationService;
    private final RuleMonitorService ruleMonitorService;

    private final ObservableList<DeviceCardViewModel> deviceViewModels = FXCollections.observableArrayList();
    private final ObservableList<RuleItemViewModel> ruleViewModels = FXCollections.observableArrayList();
    private final ObservableList<ActivityLogItemViewModel> activityLogs = FXCollections.observableArrayList();

    private final Map<String, DeviceCardViewModel> deviceMap = new HashMap<>();
    private final Map<String, RuleItemViewModel> ruleMap = new HashMap<>();

    public MainPresenter(
            DeviceControlService deviceControlService,
            RuleValidationService ruleValidationService,
            RuleMonitorService ruleMonitorService
    ) {
        this.deviceControlService = Objects.requireNonNull(deviceControlService, "deviceControlService cannot be null");
        this.ruleValidationService = Objects.requireNonNull(ruleValidationService, "ruleValidationService cannot be null");
        this.ruleMonitorService = Objects.requireNonNull(ruleMonitorService, "ruleMonitorService cannot be null");

        // Register as listener for background monitor events
        this.ruleMonitorService.addListener(this);

        loadInitialData();
    }

    private void loadInitialData() {
        // Load Devices
        List<Device> devices = deviceControlService.getAllDevices();
        Map<DeviceStateKey, String> allStates = deviceControlService.getAllStates();

        for (Device dev : devices) {
            Map<String, String> devState = new HashMap<>();
            allStates.forEach((key, val) -> {
                if (key.getDeviceId().equals(dev.getId())) {
                    devState.put(key.getStateKey(), val);
                }
            });
            DeviceCardViewModel vm = new DeviceCardViewModel(dev, devState);
            deviceViewModels.add(vm);
            deviceMap.put(dev.getId(), vm);
        }

        // Load Rules
        List<Rule> rules = ruleValidationService.findAllRules();
        for (Rule r : rules) {
            RuleItemViewModel vm = new RuleItemViewModel(r);
            ruleViewModels.add(vm);
            ruleMap.put(r.getId(), vm);
        }

        addLogEntry(new ActivityLogItemViewModel(
                Instant.now(),
                "SYSTEM",
                "UI Initialized with " + deviceViewModels.size() + " devices and " + ruleViewModels.size() + " rules.",
                "Background monitor active (interval " + ruleMonitorService.getMonitorIntervalMs() + "ms)"
        ));
    }

    public ObservableList<DeviceCardViewModel> getDeviceViewModels() {
        return deviceViewModels;
    }

    public ObservableList<RuleItemViewModel> getRuleViewModels() {
        return ruleViewModels;
    }

    public ObservableList<ActivityLogItemViewModel> getActivityLogs() {
        return activityLogs;
    }

    public DeviceControlService getDeviceControlService() {
        return deviceControlService;
    }

    public RuleValidationService getRuleValidationService() {
        return ruleValidationService;
    }

    public RuleMonitorService getRuleMonitorService() {
        return ruleMonitorService;
    }

    /**
     * Resolves a rule ID to its human-readable rule name, falling back to the raw ID
     * if no matching rule or name is available.
     *
     * @param ruleId unique rule identifier
     * @return rule name if resolved, or ruleId if unresolvable
     */
    public String resolveRuleName(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return ruleId;
        }

        // 1. Check in-memory ruleMap (O(1))
        RuleItemViewModel item = ruleMap.get(ruleId);
        if (item != null && item.getName() != null && !item.getName().isBlank()) {
            return item.getName();
        }

        // 2. Check rule cache in ruleMonitorService
        try {
            if (ruleMonitorService != null && ruleMonitorService.getRuleCache() != null) {
                Optional<Rule> cachedRule = ruleMonitorService.getRuleCache().getRule(ruleId);
                if (cachedRule.isPresent() && cachedRule.get().getName() != null && !cachedRule.get().getName().isBlank()) {
                    return cachedRule.get().getName();
                }
            }
        } catch (Exception e) {
            logger.debug("Failed checking rule cache for id={}: {}", ruleId, e.getMessage());
        }

        // 3. Check ruleValidationService
        try {
            if (ruleValidationService != null) {
                Optional<Rule> persistentRule = ruleValidationService.findRuleById(ruleId);
                if (persistentRule.isPresent() && persistentRule.get().getName() != null && !persistentRule.get().getName().isBlank()) {
                    return persistentRule.get().getName();
                }
            }
        } catch (Exception e) {
            logger.debug("Failed checking ruleValidationService for id={}: {}", ruleId, e.getMessage());
        }

        // 4. Fallback to raw ID
        return ruleId;
    }

    /**
     * Inserts an activity log item maintaining strict ascending chronological order by timestamp.
     *
     * @param item activity log item to insert
     */
    public void addLogEntry(ActivityLogItemViewModel item) {
        if (item == null) return;
        int size = activityLogs.size();
        if (size == 0 || activityLogs.get(size - 1).compareTo(item) <= 0) {
            activityLogs.add(item);
        } else {
            // Find insertion index in ascending order
            int i = size - 1;
            while (i >= 0 && activityLogs.get(i).compareTo(item) > 0) {
                i--;
            }
            activityLogs.add(i + 1, item);
        }
    }

    public void onRuleSaved(Rule savedRule) {
        runOnFxThread(() -> {
            RuleItemViewModel existing = ruleMap.get(savedRule.getId());
            if (existing != null) {
                existing.setRule(savedRule);
            } else {
                RuleItemViewModel newVm = new RuleItemViewModel(savedRule);
                ruleViewModels.add(newVm);
                ruleMap.put(savedRule.getId(), newVm);
            }

            addLogEntry(new ActivityLogItemViewModel(
                    Instant.now(),
                    "RULE_SAVED",
                    "Rule '" + savedRule.getName() + "' saved successfully (" + savedRule.getTier() + ")",
                    "ID: " + savedRule.getId() + " | Enabled: " + savedRule.isEnabled()
            ));
        });
    }

    // --- EngineEventListener Callbacks (Marshalled to JavaFX Application Thread) ---

    @Override
    public void onStateChanged(DeviceStateKey key, String oldValue, String newValue) {
        runOnFxThread(() -> {
            DeviceCardViewModel vm = deviceMap.get(key.getDeviceId());
            if (vm != null) {
                vm.updateState(key.getStateKey(), newValue);
            }

            addLogEntry(new ActivityLogItemViewModel(
                    Instant.now(),
                    "STATE_CHANGE",
                    String.format("%s.%s changed: '%s' -> '%s'", key.getDeviceId(), key.getStateKey(), oldValue, newValue),
                    ""
            ));
        });
    }

    @Override
    public void onRuleFired(Rule rule, List<Action> appliedActions) {
        runOnFxThread(() -> {
            RuleItemViewModel vm = ruleMap.get(rule.getId());
            if (vm != null) {
                vm.updateLastFired(TIME_FORMATTER.format(Instant.now()));
            }

            String actionsSummary = appliedActions.stream()
                    .map(a -> a.deviceId() + "." + a.stateKey() + "=" + a.targetValue())
                    .collect(Collectors.joining(", "));

            addLogEntry(new ActivityLogItemViewModel(
                    Instant.now(),
                    "RULE_FIRED",
                    String.format("Rule '%s' (%s) fired", rule.getName(), rule.getTier()),
                    "Actions: [" + actionsSummary + "]"
            ));
        });
    }

    @Override
    public void onOverride(OverrideRecord record) {
        runOnFxThread(() -> {
            // Resolve rule names for winning and overridden rules via available lookup
            String winnerName = resolveRuleName(record.winningRuleId());
            String overriddenName = resolveRuleName(record.overriddenRuleId());

            String summary = String.format("Rule '%s' (%s) overrode '%s' (%s) on %s",
                    winnerName, record.winningTier(),
                    overriddenName, record.overriddenTier(),
                    record.targetKey());

            String reason = record.reason();
            if (reason != null && !reason.isBlank()) {
                if (!winnerName.equals(record.winningRuleId())) {
                    reason = reason.replace(record.winningRuleId(), winnerName);
                }
                if (!overriddenName.equals(record.overriddenRuleId())) {
                    reason = reason.replace(record.overriddenRuleId(), overriddenName);
                }
            }

            String details = String.format("Applied: '%s' (Dropped: '%s')%s",
                    record.appliedTargetValue(),
                    record.overriddenTargetValue(),
                    (reason != null && !reason.isBlank()) ? " - " + reason : "");

            addLogEntry(new ActivityLogItemViewModel(
                    record.timestamp(),
                    "OVERRIDE",
                    summary,
                    details
            ));
        });
    }

    @Override
    public void onError(String message, Throwable error) {
        runOnFxThread(() -> {
            addLogEntry(new ActivityLogItemViewModel(
                    Instant.now(),
                    "ERROR",
                    message,
                    error != null ? error.getClass().getSimpleName() + ": " + error.getMessage() : ""
            ));
        });
    }

    private void runOnFxThread(Runnable action) {
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } catch (IllegalStateException e) {
            // Toolkit not initialized (e.g. headless unit test environments)
            action.run();
        }
    }
}
