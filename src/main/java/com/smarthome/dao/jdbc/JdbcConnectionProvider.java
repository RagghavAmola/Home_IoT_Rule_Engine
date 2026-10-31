package com.smarthome.dao.jdbc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Properties;

/**
 * Default JDBC {@link ConnectionProvider} that loads database connection configuration
 * from external {@code config/app.properties} with fallback to classpath {@code /config/app.properties}.
 * <p>
 * <b>Thread-safety strategy:</b> Configuration fields are immutable and read-only.
 * Transactional connections are bound to the calling thread via {@link ThreadLocal}.
 * Concurrent threads safely obtain separate physical connections or their own thread-bound transactional connections.
 * </p>
 */
public class JdbcConnectionProvider implements ConnectionProvider {

    private static final Logger logger = LoggerFactory.getLogger(JdbcConnectionProvider.class);

    private static final String DEFAULT_CLASSPATH_CONFIG = "/config/app.properties";
    private static final String EXTERNAL_CONFIG_PATH = "config/app.properties";

    private final String url;
    private final String user;
    private final String password;
    private final String dbType;

    private final ThreadLocal<Connection> threadBoundConnection = new ThreadLocal<>();

    /**
     * Constructs a provider using parameters loaded from application configuration files.
     */
    public JdbcConnectionProvider() {
        Properties props = loadMergedProperties();
        this.url = Objects.requireNonNull(props.getProperty("db.url"), "db.url must be specified in app.properties");
        this.user = props.getProperty("db.user", "");
        this.password = props.getProperty("db.password", "");
        this.dbType = props.getProperty("db.type", "sqlite");
        logger.info("Initialized JdbcConnectionProvider for db.type='{}' with URL='{}'", dbType, url);
    }

    /**
     * Constructs a provider with explicit connection parameters.
     *
     * @param url      JDBC connection URL
     * @param user     database username (optional)
     * @param password database password (optional)
     */
    public JdbcConnectionProvider(String url, String user, String password) {
        this.url = Objects.requireNonNull(url, "url cannot be null");
        this.user = user != null ? user : "";
        this.password = password != null ? password : "";
        this.dbType = url.startsWith("jdbc:sqlite") ? "sqlite" : "mysql";
    }

    private Connection openConnection() throws SQLException {
    Connection conn = DriverManager.getConnection(url, user, password);
    if ("sqlite".equalsIgnoreCase(dbType) || url.startsWith("jdbc:sqlite")) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
    }
    return conn;
    }

    /**
     * Factory method creating an in-memory SQLite connection provider.
     *
     * @param dbName unique identifier for the in-memory shared database instance
     * @return in-memory connection provider
     */
    public static JdbcConnectionProvider createInMemory(String dbName) {
        String memUrl = "jdbc:sqlite:file:" + dbName + "?mode=memory&cache=shared";
        return new JdbcConnectionProvider(memUrl, "", "");
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection bound = threadBoundConnection.get();
        if (bound != null && !bound.isClosed()) {
            // Return close-suppressing proxy so try-with-resources in DAOs won't close transactional connection
            return createCloseSuppressingProxy(bound);
        }

        return openConnection();
    }

    /**
     * Obtains the raw, unproxied physical connection for transaction management.
     *
     * @return raw JDBC connection
     * @throws SQLException on error
     */
    Connection getRawConnection() throws SQLException {
        return openConnection();
    }

    /**
     * Binds a physical connection to the current thread for transaction participation.
     *
     * @param connection active transactional connection
     */
    void bindThreadConnection(Connection connection) {
        threadBoundConnection.set(connection);
    }

    /**
     * Unbinds the transactional connection from the current thread.
     */
    void unbindThreadConnection() {
        threadBoundConnection.remove();
    }
    boolean hasThreadBoundConnection() {
    Connection bound = threadBoundConnection.get();
        try {
            return bound != null && !bound.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    private Connection createCloseSuppressingProxy(Connection target) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                    return null;
                }
                try {
                    return method.invoke(target, args);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            }
        );
    }

    private Properties loadMergedProperties() {
        Properties properties = new Properties();

        // 1. Load defaults from classpath
        try (InputStream cpStream = getClass().getResourceAsStream(DEFAULT_CLASSPATH_CONFIG)) {
            if (cpStream != null) {
                properties.load(cpStream);
            }
        } catch (Exception e) {
            logger.warn("Could not load classpath defaults from {}", DEFAULT_CLASSPATH_CONFIG, e);
        }

        // 2. Override with external file if present
        File extFile = new File(EXTERNAL_CONFIG_PATH);
        if (extFile.exists() && extFile.isFile()) {
            try (InputStream extStream = new FileInputStream(extFile)) {
                properties.load(extStream);
                logger.info("Applied external configuration overrides from {}", extFile.getAbsolutePath());
            } catch (Exception e) {
                logger.warn("Could not read external configuration file {}", extFile.getAbsolutePath(), e);
            }
        }

        return properties;
    }

    public String getUrl() {
        return url;
    }

    public String getDbType() {
        return dbType;
    }
}

