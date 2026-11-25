package com.smarthome.service.monitor;

import com.smarthome.dao.DeviceStateDao;
import com.smarthome.dao.ExecutionLogDao;
import com.smarthome.dao.RuleDao;
import com.smarthome.dao.TransactionManager;
import com.smarthome.domain.Action;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.domain.OverrideRecord;
import com.smarthome.domain.Rule;
import com.smarthome.domain.RuleExecutionRecord;
import com.smarthome.engine.EvaluationResult;
import com.smarthome.engine.RuleEngine;
import com.smarthome.engine.StateSnapshot;
import com.smarthome.engine.priority.TriggeredRule;
import com.smarthome.simulation.DeviceStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background monitoring service evaluating active rules and persisting state and audit logs.
 */
public class RuleMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(RuleMonitorService.class);
    public static final long DEFAULT_MONITOR_INTERVAL_MS = 2000L;

    private final DeviceStateStore stateStore;
    private final RuleEngine ruleEngine;
    private final RuleCache ruleCache;
    private final DeviceStateDao deviceStateDao;
    private final ExecutionLogDao executionLogDao;
    private final TransactionManager transactionManager;
    private final long monitorIntervalMs;

    private final CopyOnWriteArrayList<EngineEventListener> listeners = new CopyOnWriteArrayList<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private final Object lifecycleLock = new Object();

    private volatile ScheduledExecutorService executor;

    /**
     * Primary constructor with all dependencies.
     */
    public RuleMonitorService(
            DeviceStateStore stateStore,
            RuleEngine ruleEngine,
            RuleCache ruleCache,
            DeviceStateDao deviceStateDao,
            ExecutionLogDao executionLogDao,
            TransactionManager transactionManager,
            long monitorIntervalMs
    ) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore cannot be null");
        this.ruleEngine = Objects.requireNonNull(ruleEngine, "ruleEngine cannot be null");
        this.ruleCache = Objects.requireNonNull(ruleCache, "ruleCache cannot be null");
        this.deviceStateDao = deviceStateDao;
        this.executionLogDao = executionLogDao;
        this.transactionManager = transactionManager;
        if (monitorIntervalMs <= 0) {
            throw new IllegalArgumentException("monitorIntervalMs must be positive, but was: " + monitorIntervalMs);
        }
        this.monitorIntervalMs = monitorIntervalMs;
    }

    /**
     * Convenience constructor without transaction manager.
     */
    public RuleMonitorService(
            DeviceStateStore stateStore,
            RuleEngine ruleEngine,
            RuleCache ruleCache,
            DeviceStateDao deviceStateDao,
            ExecutionLogDao executionLogDao,
            long monitorIntervalMs
    ) {
        this(stateStore, ruleEngine, ruleCache, deviceStateDao, executionLogDao, null, monitorIntervalMs);
    }

    /**
     * Convenience constructor initializing RuleCache from RuleDao.
     */
    public RuleMonitorService(
            DeviceStateStore stateStore,
            RuleEngine ruleEngine,
            RuleDao ruleDao,
            DeviceStateDao deviceStateDao,
            ExecutionLogDao executionLogDao,
            long monitorIntervalMs
    ) {
        this(stateStore, ruleEngine, new RuleCache(ruleDao), deviceStateDao, executionLogDao, null, monitorIntervalMs);
    }

    /**
     * Lightweight constructor for standalone in-memory or testing usage.
     */
    public RuleMonitorService(DeviceStateStore stateStore, RuleCache ruleCache) {
        this(stateStore, new RuleEngine(), ruleCache, null, null, null, DEFAULT_MONITOR_INTERVAL_MS);
    }

    // --- Lifecycle Methods ---

    /**
     * Starts the background evaluation monitor.
     * <p>
     * Initializes the "rule-monitor" daemon thread and begins periodic scheduling.
     * </p>
     */
    public void start() {
        synchronized (lifecycleLock) {
            if (isShutdown.get()) {
                throw new IllegalStateException("Cannot start RuleMonitorService after it has been shut down");
            }
            if (running.get()) {
                logger.debug("RuleMonitorService is already running");
                return;
            }

            if (ruleCache.size() == 0) {
                ruleCache.refresh();
            }

            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "rule-monitor");
                thread.setDaemon(true);
                return thread;
            });

            running.set(true);
            paused.set(false);

            executor.scheduleWithFixedDelay(
                    this::tickSafe,
                    monitorIntervalMs,
                    monitorIntervalMs,
                    TimeUnit.MILLISECONDS
            );

            logger.info("RuleMonitorService started (interval={}ms, thread='rule-monitor')", monitorIntervalMs);
        }
    }

    /**
     * Pauses the evaluation ticks. Scheduled triggers will be skipped until resumed.
     */
    public void pause() {
        if (running.get() && paused.compareAndSet(false, true)) {
            logger.info("RuleMonitorService paused");
        }
    }

    /**
     * Resumes evaluation ticks after being paused.
     */
    public void resume() {
        if (running.get() && paused.compareAndSet(true, false)) {
            logger.info("RuleMonitorService resumed");
        }
    }

    /**
     * Gracefully shuts down the background evaluation monitor.
     * Awaits completion of any in-flight evaluation tick up to 5 seconds.
     */
    public void shutdown() {
        synchronized (lifecycleLock) {
            if (isShutdown.get()) {
                return;
            }
            isShutdown.set(true);
            running.set(false);
            paused.set(false);

            ScheduledExecutorService currentExecutor = this.executor;
            if (currentExecutor != null) {
                currentExecutor.shutdown();
                try {
                    if (!currentExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.warn("RuleMonitorService executor did not terminate gracefully within 5s, forcing shutdown");
                        currentExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    currentExecutor.shutdownNow();
                }
            }
            logger.info("RuleMonitorService shutdown complete");
        }
    }

    public boolean isRunning() {
        return running.get() && !paused.get() && !isShutdown.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    public boolean isShutdown() {
        return isShutdown.get();
    }

    public long getMonitorIntervalMs() {
        return monitorIntervalMs;
    }

    public RuleCache getRuleCache() {
        return ruleCache;
    }

    public DeviceStateStore getStateStore() {
        return stateStore;
    }

    public RuleEngine getRuleEngine() {
        return ruleEngine;
    }

    // --- Event Listener Registration ---

    public void addListener(EngineEventListener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        listeners.addIfAbsent(listener);
    }

    public void removeListener(EngineEventListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public int listenerCount() {
        return listeners.size();
    }

    // --- Evaluation Tick Execution ---

    /**
     * Exception-safe wrapper called periodically by the scheduler.
     * Guarantees that any uncaught exception in a single tick will never kill the schedule.
     */
    private void tickSafe() {
        if (paused.get() || isShutdown.get() || !running.get()) {
            return;
        }
        try {
            executeTick();
        } catch (Throwable t) {
            logger.error("Error during RuleMonitorService evaluation tick: {}", t.getMessage(), t);
            notifyError("Evaluation tick failed: " + t.getMessage(), t);
        }
    }

    /**
     * Executes a single evaluation tick synchronously.
     * <p>
     * Can be invoked directly by test fixtures or UI action handlers ("Evaluate Now").
     * </p>
     *
     * @return {@link TickResult} summarizing actions, rules, and overrides from this tick
     */
    public TickResult executeTick() {
        long start = System.currentTimeMillis();

        StateSnapshot snapshot = stateStore.snapshot();
        List<Rule> enabledRules = ruleCache.getEnabledRules();

        if (enabledRules.isEmpty()) {
            return new TickResult(0, 0, List.of(), List.of(), Math.max(0L, System.currentTimeMillis() - start), false);
        }

        EvaluationResult result = ruleEngine.evaluate(snapshot, enabledRules);

        List<Action> appliedActions = new ArrayList<>();
        Map<DeviceStateKey, String> stateBatch = new HashMap<>();

        for (Action action : result.actionsToExecute()) {
            DeviceStateKey key = action.getTargetStateKey();
            String currentVal = snapshot.get(key);
            if (!Objects.equals(currentVal, action.targetValue())) {
                boolean changed = stateStore.applyAction(action);
                if (changed) {
                    appliedActions.add(action);
                    stateBatch.put(key, action.targetValue());
                    notifyStateChanged(key, currentVal, action.targetValue());
                }
            }
        }

        List<RuleExecutionRecord> executionRecords = new ArrayList<>();
        for (TriggeredRule triggered : result.triggeredRules()) {
            Rule rule = triggered.rule();
            List<Action> ruleApplied = triggered.actions().stream()
                    .filter(appliedActions::contains)
                    .toList();

            RuleExecutionRecord record = new RuleExecutionRecord(
                    UUID.randomUUID().toString(),
                    rule.getId(),
                    rule.getName(),
                    rule.getTier(),
                    Instant.now(),
                    true,
                    rule.getActions(),
                    snapshot.asMap(),
                    "Rule triggered successfully (" + ruleApplied.size() + " actions applied)"
            );
            executionRecords.add(record);
            notifyRuleFired(rule, ruleApplied);
        }

        for (OverrideRecord override : result.overrides()) {
            notifyOverride(override);
        }

        boolean persistenceError = persistAuditLogs(stateBatch, executionRecords, result.overrides());

        long duration = Math.max(0L, System.currentTimeMillis() - start);
        return new TickResult(
                enabledRules.size(),
                result.triggeredRules().size(),
                appliedActions,
                result.overrides(),
                duration,
                persistenceError
        );
    }

    private boolean persistAuditLogs(
            Map<DeviceStateKey, String> stateBatch,
            List<RuleExecutionRecord> executionRecords,
            List<OverrideRecord> overrides
    ) {
        if (stateBatch.isEmpty() && executionRecords.isEmpty() && overrides.isEmpty()) {
            return false;
        }

        Runnable persistTask = () -> {
            if (!stateBatch.isEmpty() && deviceStateDao != null) {
                deviceStateDao.batchUpsertStates(stateBatch);
            }
            if (executionLogDao != null) {
                for (RuleExecutionRecord r : executionRecords) {
                    executionLogDao.saveExecutionRecord(r);
                }
                for (OverrideRecord o : overrides) {
                    executionLogDao.saveOverrideRecord(o);
                }
            }
        };

        if (transactionManager != null) {
            try {
                transactionManager.executeInTransaction(persistTask::run);
                return false;
            } catch (Exception e) {
                logger.error("Failed to commit batch persistence in transaction: {}", e.getMessage(), e);
                notifyError("Transaction failed during batch persistence: " + e.getMessage(), e);
                return true;
            }
        } else {
            try {
                persistTask.run();
                return false;
            } catch (Exception e) {
                logger.error("Failed to persist state/audit records via DAOs: {}", e.getMessage(), e);
                notifyError("DAO persistence failed: " + e.getMessage(), e);
                return true;
            }
        }
    }

    // --- Listener Dispatch Helpers ---

    private void notifyStateChanged(DeviceStateKey key, String oldVal, String newVal) {
        for (EngineEventListener listener : listeners) {
            try {
                listener.onStateChanged(key, oldVal, newVal);
            } catch (Throwable t) {
                logger.warn("Listener threw exception in onStateChanged: {}", t.getMessage(), t);
            }
        }
    }

    private void notifyRuleFired(Rule rule, List<Action> appliedActions) {
        for (EngineEventListener listener : listeners) {
            try {
                listener.onRuleFired(rule, appliedActions);
            } catch (Throwable t) {
                logger.warn("Listener threw exception in onRuleFired: {}", t.getMessage(), t);
            }
        }
    }

    private void notifyOverride(OverrideRecord record) {
        for (EngineEventListener listener : listeners) {
            try {
                listener.onOverride(record);
            } catch (Throwable t) {
                logger.warn("Listener threw exception in onOverride: {}", t.getMessage(), t);
            }
        }
    }

    private void notifyError(String message, Throwable error) {
        for (EngineEventListener listener : listeners) {
            try {
                listener.onError(message, error);
            } catch (Throwable t) {
                logger.warn("Listener threw exception in onError: {}", t.getMessage(), t);
            }
        }
    }
}