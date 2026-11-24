package com.smarthome.config;

import com.smarthome.dao.DeviceDao;
import com.smarthome.dao.DeviceStateDao;
import com.smarthome.dao.ExecutionLogDao;
import com.smarthome.dao.RuleDao;
import com.smarthome.dao.TransactionManager;
import com.smarthome.dao.jdbc.DatabaseInitializer;
import com.smarthome.dao.jdbc.JdbcConnectionProvider;
import com.smarthome.dao.jdbc.JdbcDeviceDao;
import com.smarthome.dao.jdbc.JdbcDeviceStateDao;
import com.smarthome.dao.jdbc.JdbcExecutionLogDao;
import com.smarthome.dao.jdbc.JdbcRuleDao;
import com.smarthome.dao.jdbc.JdbcTransactionManager;
import com.smarthome.domain.Device;
import com.smarthome.domain.DeviceStateKey;
import com.smarthome.engine.RuleEngine;
import com.smarthome.engine.graph.RuleDependencyGraph;
import com.smarthome.service.DeviceControlService;
import com.smarthome.service.DeviceControlServiceImpl;
import com.smarthome.service.RuleValidationService;
import com.smarthome.service.RuleValidationServiceImpl;
import com.smarthome.service.monitor.RuleCache;
import com.smarthome.service.monitor.RuleMonitorService;
import com.smarthome.simulation.DeviceStateStore;
import com.smarthome.simulation.SimulatedDeviceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Application bootstrap and manual dependency injection wiring container.
 * <p>
 * Responsible for loading configuration, establishing database connectivity, running schema migrations,
 * instantiating DAOs, setting up the in-memory simulation store, wiring services, and starting background monitors.
 * </p>
 */
public class AppBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(AppBootstrap.class);

    private static final String CLASSPATH_CONFIG = "/config/app.properties";
    private static final String EXTERNAL_CONFIG = "config/app.properties";

    private final Properties properties = new Properties();

    private JdbcConnectionProvider connectionProvider;
    private DeviceDao deviceDao;
    private DeviceStateDao deviceStateDao;
    private RuleDao ruleDao;
    private ExecutionLogDao executionLogDao;
    private TransactionManager transactionManager;

    private DeviceStateStore stateStore;
    private RuleEngine ruleEngine;
    private RuleCache ruleCache;
    private RuleDependencyGraph dependencyGraph;

    private DeviceControlService deviceControlService;
    private RuleValidationService ruleValidationService;
    private RuleMonitorService ruleMonitorService;

    public static AppBootstrap start() {
        AppBootstrap bootstrap = new AppBootstrap();
        bootstrap.initialize();
        return bootstrap;
    }

    /**
     * Initializes all application layers and begins background monitoring.
     */
    public void initialize() {
        logger.info("Starting Smart Home IoT Rule Engine bootstrap initialization...");
        loadProperties();

        // 1. Establish Database Connection & Run Initializer
        String dbUrl = properties.getProperty("db.url", "jdbc:sqlite:smarthome.db");
        String dbUser = properties.getProperty("db.user", "");
        String dbPass = properties.getProperty("db.password", "");

        this.connectionProvider = new JdbcConnectionProvider(dbUrl, dbUser, dbPass);
        new DatabaseInitializer(connectionProvider).initialize(true);

        // 2. Instantiate DAOs and Transaction Manager
        this.transactionManager = new JdbcTransactionManager(connectionProvider);
        this.deviceDao = new JdbcDeviceDao(connectionProvider);
        this.deviceStateDao = new JdbcDeviceStateDao(connectionProvider);
        this.ruleDao = new JdbcRuleDao(connectionProvider);
        this.executionLogDao = new JdbcExecutionLogDao(connectionProvider);

        // 3. Setup Simulation & In-Memory State Store
        this.stateStore = new DeviceStateStore();
        populateInitialStateStore();

        // 4. Setup Engine, Graph & Caches
        this.ruleEngine = new RuleEngine();
        this.dependencyGraph = new RuleDependencyGraph();
        this.ruleCache = new RuleCache(ruleDao);
        this.ruleCache.refresh();

        // 5. Setup Services
        this.ruleValidationService = new RuleValidationServiceImpl(ruleDao, dependencyGraph, ruleCache);
        this.deviceControlService = new DeviceControlServiceImpl(deviceDao, deviceStateDao, stateStore);

        // 6. Setup and Start Background Monitor
        long monitorInterval = Long.parseLong(properties.getProperty("monitor.interval.ms", "2000"));
        this.ruleMonitorService = new RuleMonitorService(
                stateStore,
                ruleEngine,
                ruleCache,
                deviceStateDao,
                executionLogDao,
                transactionManager,
                monitorInterval
        );
        this.ruleMonitorService.start();

        logger.info("Bootstrap initialization complete. Background monitor active.");
    }

    private void loadProperties() {
        // Try external config file first, then fall back to classpath default
        File externalFile = new File(EXTERNAL_CONFIG);
        if (externalFile.exists() && externalFile.isFile()) {
            try (InputStream in = new FileInputStream(externalFile)) {
                properties.load(in);
                logger.info("Loaded configuration from external file: {}", externalFile.getAbsolutePath());
                return;
            } catch (Exception e) {
                logger.warn("Failed to load external config file {}: {}", externalFile.getAbsolutePath(), e.getMessage());
            }
        }

        try (InputStream in = getClass().getResourceAsStream(CLASSPATH_CONFIG)) {
            if (in != null) {
                properties.load(in);
                logger.info("Loaded configuration from classpath resource: {}", CLASSPATH_CONFIG);
            } else {
                logger.warn("Classpath config {} not found; using defaults", CLASSPATH_CONFIG);
            }
        } catch (Exception e) {
            logger.error("Failed to load classpath configuration: {}", e.getMessage(), e);
        }
    }

    private void populateInitialStateStore() {
        List<Device> seededDevices = deviceDao.findAll();
        Map<DeviceStateKey, String> seededStates = deviceStateDao.findAllStates();

        for (Device device : seededDevices) {
            stateStore.registerDevice(SimulatedDeviceFactory.create(device));
        }

        for (Map.Entry<DeviceStateKey, String> entry : seededStates.entrySet()) {
            stateStore.setState(entry.getKey(), entry.getValue());
        }

        logger.info("DeviceStateStore initialized with {} devices and {} state attributes",
                seededDevices.size(), stateStore.size());
    }

    /**
     * Gracefully shuts down background monitors and connection pools.
     */
    public void shutdown() {
        logger.info("Shutting down Smart Home IoT Rule Engine application...");
        if (ruleMonitorService != null && !ruleMonitorService.isShutdown()) {
            ruleMonitorService.shutdown();
        }
        if (connectionProvider != null) {
            connectionProvider.close();
        }
        logger.info("Application shutdown complete.");
    }

    public Properties getProperties() {
        return properties;
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

    public DeviceStateStore getStateStore() {
        return stateStore;
    }

    public RuleCache getRuleCache() {
        return ruleCache;
    }

    public RuleDependencyGraph getDependencyGraph() {
        return dependencyGraph;
    }

    public ExecutionLogDao getExecutionLogDao() {
        return executionLogDao;
    }
}
