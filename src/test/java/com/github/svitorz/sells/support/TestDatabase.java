package com.github.svitorz.sells.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Starts a single embedded PostgreSQL for the whole test JVM and creates the
 * "sells" database on it. Flyway migrations are applied by Spring Boot.
 */
public final class TestDatabase {

    public static final String DATABASE = "sells";
    public static final String USERNAME = "postgres";
    public static final String PASSWORD = "";

    private static EmbeddedPostgres postgres;

    private TestDatabase() {
    }

    public static synchronized String jdbcUrl() {
        start();
        return postgres.getJdbcUrl(USERNAME, DATABASE);
    }

    private static void start() {
        if (postgres != null) {
            return;
        }
        try {
            postgres = EmbeddedPostgres.builder()
                    .setDataDirectory(Path.of("target", "test-pgdata"))
                    .setCleanDataDirectory(true)
                    .setRegisterShutdownHook(true)
                    .start();
            try (Connection connection = postgres.getPostgresDatabase().getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select 1 from pg_database where datname = '" + DATABASE + "'")) {
                if (!rs.next()) {
                    statement.execute("create database " + DATABASE);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not start the embedded PostgreSQL used by tests", e);
        }
    }
}
