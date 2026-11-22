package com.smarthome.service.monitor;

import com.smarthome.dao.RuleDao;
import com.smarthome.domain.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Thread-safe in-memory cache of automation rules.
 * <p>
 * Ensures evaluation cycles never query the database on every tick and guarantees that
 * concurrent rule modifications (e.g. from the UI thread) never corrupt or interfere
 * with an in-flight evaluation on the background monitor thread.
 * </p>
 * <p>
 * <b>Threading Model & Concurrency Strategy:</b>
 * <ul>
 *   <li><b>Hybrid Read-Write Lock & Copy-on-Write:</b> Employs a {@link ReentrantReadWriteLock}.
 *       Read operations (such as obtaining enabled rules for evaluation) acquire the read lock
 *       and return an immutable snapshot ({@link List#copyOf(Collection)}).</li>
 *   <li><b>Isolated Mutations:</b> Write operations (adding, updating, removing rules, or reloading
 *       from {@link RuleDao}) acquire the exclusive write lock, update internal storage, and atomically
 *       publish a new immutable copy-on-write list of enabled rules.</li>
 *   <li><b>In-Flight Isolation:</b> An evaluation thread holding a reference to the snapshot returned
 *       by {@link #getEnabledRules()} is completely unaffected by subsequent writes, eliminating
 *       any possibility of {@link java.util.ConcurrentModificationException} or inconsistent rule states.</li>
 * </ul>
 * </p>
 */
public class RuleCache {

    private static final Logger logger = LoggerFactory.getLogger(RuleCache.class);

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();

    private final Map<String, Rule> rulesById = new ConcurrentHashMap<>();
    private volatile List<Rule> cachedEnabledRules = List.of();

    private final RuleDao ruleDao;

    /**
     * Constructs a RuleCache with an optional backing {@link RuleDao}.
     *
     * @param ruleDao DAO used to refresh rules from persistent storage (nullable)
     */
    public RuleCache(RuleDao ruleDao) {
        this.ruleDao = ruleDao;
    }

    /**
     * Default constructor for standalone in-memory usage without direct DAO dependency.
     */
    public RuleCache() {
        this(null);
    }

    /**
     * Retrieves an immutable point-in-time snapshot of all currently enabled rules.
     *
     * @return unmodifiable list of active rules safe for concurrent iteration
     */
    public List<Rule> getEnabledRules() {
        readLock.lock();
        try {
            return cachedEnabledRules;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Retrieves a rule by its ID if present in the cache.
     *
     * @param ruleId unique rule identifier
     * @return Optional containing the cached Rule
     */
    public Optional<Rule> getRule(String ruleId) {
        if (ruleId == null) return Optional.empty();
        readLock.lock();
        try {
            return Optional.ofNullable(rulesById.get(ruleId));
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Retrieves an immutable list of all rules (both enabled and disabled) currently cached.
     *
     * @return unmodifiable list of all rules
     */
    public List<Rule> getAllRules() {
        readLock.lock();
        try {
            return List.copyOf(rulesById.values());
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Inserts or updates a rule in the cache, atomically updating the enabled rules snapshot.
     *
     * @param rule the rule to cache
     */
    public void updateRule(Rule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        writeLock.lock();
        try {
            rulesById.put(rule.getId(), rule);
            rebuildEnabledRulesSnapshot();
            logger.debug("RuleCache updated rule id='{}', enabled={}", rule.getId(), rule.isEnabled());
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Removes a rule by its identifier from the cache, atomically updating the snapshot.
     *
     * @param ruleId identifier of the rule to remove
     * @return true if the rule was present and removed, false otherwise
     */
    public boolean removeRule(String ruleId) {
        if (ruleId == null) return false;
        writeLock.lock();
        try {
            Rule removed = rulesById.remove(ruleId);
            if (removed != null) {
                rebuildEnabledRulesSnapshot();
                logger.debug("RuleCache removed rule id='{}'", ruleId);
                return true;
            }
            return false;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Replaces the entire cache contents with the provided collection of rules.
     *
     * @param rules collection of rules to populate
     */
    public void setRules(Collection<Rule> rules) {
        Objects.requireNonNull(rules, "rules cannot be null");
        writeLock.lock();
        try {
            replaceAllInternal(rules);
            logger.info("RuleCache populated with {} rules ({} enabled)", rulesById.size(), cachedEnabledRules.size());
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Refreshes the cache from the backing {@link RuleDao} if one was configured.
     *
     * @return number of enabled rules loaded, or -1 if no DAO is wired
     */
    public int refresh() {
        if (ruleDao == null) {
            logger.warn("RuleCache.refresh() invoked but no RuleDao is configured");
            return -1;
        }

        List<Rule> allRules = ruleDao.findAll();
        Collection<Rule> safeRules = allRules != null ? allRules : List.of();

        writeLock.lock();
        try {
            replaceAllInternal(safeRules);
            logger.info("RuleCache refreshed from DAO: {} total, {} enabled", rulesById.size(), cachedEnabledRules.size());
            return cachedEnabledRules.size();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Executes a supplier under the cache's read lock.
     *
     * @param action supplier to execute
     * @param <T>    return type
     * @return supplier outcome
     */
    public <T> T withReadLock(Supplier<T> action) {
        Objects.requireNonNull(action, "action cannot be null");
        readLock.lock();
        try {
            return action.get();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Executes a runnable under the cache's write lock.
     *
     * @param action runnable to execute
     */
    public void withWriteLock(Runnable action) {
        Objects.requireNonNull(action, "action cannot be null");
        writeLock.lock();
        try {
            action.run();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Total number of rules stored in cache.
     *
     * @return rule count
     */
    public int size() {
        readLock.lock();
        try {
            return rulesById.size();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Clears all cached rules.
     */
    public void clear() {
        writeLock.lock();
        try {
            rulesById.clear();
            cachedEnabledRules = List.of();
        } finally {
            writeLock.unlock();
        }
    }

    private void replaceAllInternal(Collection<Rule> rules) {
        rulesById.clear();
        for (Rule rule : rules) {
            if (rule != null) {
                rulesById.put(rule.getId(), rule);
            }
        }
        rebuildEnabledRulesSnapshot();
    }

    private void rebuildEnabledRulesSnapshot() {
        List<Rule> enabled = new ArrayList<>();
        for (Rule r : rulesById.values()) {
            if (r.isEnabled()) {
                enabled.add(r);
            }
        }
        this.cachedEnabledRules = List.copyOf(enabled);
    }
}