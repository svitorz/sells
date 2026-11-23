package com.github.svitorz.sells.infrastructure.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseBootstrapTest {

    @TempDir
    Path dir;

    private DatabaseSettings embeddedSettings() {
        return new DatabaseSettings(DatabaseSettings.Mode.EMBEDDED, dir, null, null, null);
    }

    private static String url(DatabaseBootstrap bootstrap) {
        return (String) bootstrap.springProperties().get("spring.datasource.url");
    }

    private static void execute(String url, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "postgres", "");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String queryString(String url, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "postgres", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    @Test
    void startsWithEmptyDirectoryAndCreatesDatabase() throws Exception {
        try (DatabaseBootstrap bootstrap = DatabaseBootstrap.start(embeddedSettings())) {
            assertEquals("sells", queryString(url(bootstrap), "select current_database()"));
            assertTrue(Files.isDirectory(dir.resolve("pgdata")));
            assertTrue(Files.exists(dir.resolve("sells.lock")));
        }
    }

    @Test
    void keepsDataAcrossRestarts() throws Exception {
        try (DatabaseBootstrap first = DatabaseBootstrap.start(embeddedSettings())) {
            execute(url(first), "create table probe (value text)");
            execute(url(first), "insert into probe (value) values ('kept')");
        }

        try (DatabaseBootstrap second = DatabaseBootstrap.start(embeddedSettings())) {
            assertEquals("kept", queryString(url(second), "select value from probe"));
        }
    }

    @Test
    void rejectsSecondInstanceOnSameDataDirectory() throws Exception {
        try (DatabaseBootstrap first = DatabaseBootstrap.start(embeddedSettings())) {
            assertThrows(InstanceAlreadyRunningException.class,
                    () -> DatabaseBootstrap.start(embeddedSettings()));
        }
    }

    @Test
    void closeIsIdempotent() {
        DatabaseBootstrap bootstrap = DatabaseBootstrap.start(embeddedSettings());

        bootstrap.close();
        assertDoesNotThrow(bootstrap::close);
    }

    @Test
    void runsWithDurableSettings() throws Exception {
        try (DatabaseBootstrap bootstrap = DatabaseBootstrap.start(embeddedSettings())) {
            // If this test fails, stop: the library's -F / synchronous_commit=off were not overridden
            // and sales could be lost on power failure (ADR-0004, item 9).
            assertEquals("on", queryString(url(bootstrap), "show fsync"));
            assertEquals("on", queryString(url(bootstrap), "show synchronous_commit"));
        }
    }

    @Test
    void externalModeOnlyForwardsSettings() {
        DatabaseSettings external = new DatabaseSettings(DatabaseSettings.Mode.EXTERNAL, dir,
                "jdbc:postgresql://example:5432/x", "app", "secret");

        try (DatabaseBootstrap bootstrap = DatabaseBootstrap.start(external)) {
            assertEquals("jdbc:postgresql://example:5432/x", url(bootstrap));
            assertFalse(Files.exists(dir.resolve("pgdata")));
        }
    }
}
