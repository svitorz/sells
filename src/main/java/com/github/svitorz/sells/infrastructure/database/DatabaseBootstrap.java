package com.github.svitorz.sells.infrastructure.database;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts the database before Spring (ADR-0004) and stops it on shutdown.
 * In EMBEDDED mode it owns a PostgreSQL process; in EXTERNAL mode it only
 * forwards the configured connection settings.
 */
public final class DatabaseBootstrap implements AutoCloseable {

    static final String DATABASE_NAME = "sells";
    static final String EMBEDDED_USER = "postgres";

    private static final Logger log = LoggerFactory.getLogger(DatabaseBootstrap.class);

    private final DatabaseSettings settings;
    private final InstanceLock lock;
    private final EmbeddedPostgres postgres;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private DatabaseBootstrap(DatabaseSettings settings, InstanceLock lock, EmbeddedPostgres postgres,
                              String jdbcUrl, String username, String password) {
        this.settings = settings;
        this.lock = lock;
        this.postgres = postgres;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public static DatabaseBootstrap start(DatabaseSettings settings) {
        if (settings.mode() == DatabaseSettings.Mode.EXTERNAL) {
            log.info("Using external database {}", settings.url());
            return new DatabaseBootstrap(settings, null, null,
                    settings.url(), settings.username(), settings.password());
        }
        return startEmbedded(settings);
    }

    private static DatabaseBootstrap startEmbedded(DatabaseSettings settings) {
        InstanceLock lock = null;
        EmbeddedPostgres postgres = null;
        try {
            lock = InstanceLock.acquire(settings.dataDir().resolve("sells.lock"));
            Path runtimeDir = Files.createDirectories(settings.dataDir().resolve("runtime"));
            log.info("Starting embedded PostgreSQL in {}", settings.dataDir());
            postgres = EmbeddedPostgres.builder()
                    .setDataDirectory(settings.dataDir().resolve("pgdata"))
                    .setCleanDataDirectory(false)
                    .setOverrideWorkingDirectory(runtimeDir.toFile())
                    .setRegisterShutdownHook(false)
                    .setPGStartupWait(Duration.ofSeconds(30))
                    .setServerConfig("listen_addresses", "localhost")
                    // The library is meant for tests: it starts postgres with -F (fsync off)
                    // and synchronous_commit=off. A sales system must survive power loss,
                    // so both are turned back on. The -c options come after -F and win.
                    .setServerConfig("fsync", "on")
                    .setServerConfig("synchronous_commit", "on")
                    .setServerConfig("full_page_writes", "on")
                    .start();
            ensureDatabaseExists(postgres);
            DatabaseBootstrap bootstrap = new DatabaseBootstrap(settings, lock, postgres,
                    postgres.getJdbcUrl(EMBEDDED_USER, DATABASE_NAME), EMBEDDED_USER, "");
            Runtime.getRuntime().addShutdownHook(new Thread(bootstrap::close, "sells-database-shutdown"));
            log.info("Embedded PostgreSQL started on port {}", postgres.getPort());
            return bootstrap;
        } catch (InstanceAlreadyRunningException e) {
            throw e;
        } catch (IOException | SQLException | RuntimeException e) {
            closeQuietly(postgres);
            closeQuietly(lock);
            throw new DatabaseStartupException("Could not start the embedded PostgreSQL", e);
        }
    }

    private static void ensureDatabaseExists(EmbeddedPostgres postgres) throws SQLException {
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement()) {
            boolean exists;
            try (ResultSet rs = statement.executeQuery(
                    "select 1 from pg_database where datname = '" + DATABASE_NAME + "'")) {
                exists = rs.next();
            }
            if (!exists) {
                statement.execute("create database " + DATABASE_NAME);
                log.info("Created database {}", DATABASE_NAME);
            }
        }
    }

    /**
     * Properties handed to SpringApplicationBuilder#properties. They have the
     * lowest precedence, so application.properties must not define them.
     */
    public Map<String, Object> springProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", jdbcUrl);
        properties.put("spring.datasource.username", username == null ? "" : username);
        properties.put("spring.datasource.password", password == null ? "" : password);
        properties.put("logging.file.name", settings.dataDir().resolve("logs").resolve("sells.log").toString());
        return properties;
    }

    public DatabaseSettings settings() {
        return settings;
    }

    /** Stops PostgreSQL (pg_ctl stop -m fast) and releases the lock. Safe to call more than once. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeQuietly(postgres);
        closeQuietly(lock);
        if (postgres != null) {
            log.info("Embedded PostgreSQL stopped");
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            log.warn("Error while closing {}", closeable.getClass().getSimpleName(), e);
        }
    }
}
