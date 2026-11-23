package com.github.svitorz.sells.infrastructure.database;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;

/**
 * Database configuration resolved before Spring starts.
 */
public record DatabaseSettings(Mode mode, Path dataDir, String url, String username, String password) {

    public enum Mode {
        EMBEDDED,
        EXTERNAL
    }

    public static DatabaseSettings fromEnvironment() {
        return from(DatabaseSettings::lookupSystem);
    }

    static DatabaseSettings from(Function<String, String> lookup) {
        String modeValue = valueOrDefault(lookup.apply("sells.db.mode"), "embedded");
        Mode mode = switch (modeValue.trim().toLowerCase(Locale.ROOT)) {
            case "embedded" -> Mode.EMBEDDED;
            case "external" -> Mode.EXTERNAL;
            default -> throw new IllegalArgumentException(
                    "Invalid sells.db.mode '" + modeValue + "'. Use 'embedded' or 'external'.");
        };
        String dataDirValue = lookup.apply("sells.data.dir");
        Path dataDir = dataDirValue != null
                ? Path.of(dataDirValue)
                : Path.of(System.getProperty("user.home"), ".sells");
        String url = lookup.apply("sells.db.url");
        if (mode == Mode.EXTERNAL && url == null) {
            throw new IllegalArgumentException("sells.db.url is required when sells.db.mode=external");
        }
        return new DatabaseSettings(mode, dataDir.toAbsolutePath(), url,
                lookup.apply("sells.db.username"), lookup.apply("sells.db.password"));
    }

    private static String lookupSystem(String key) {
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String env = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        return env == null || env.isBlank() ? null : env;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
