package com.github.svitorz.sells.infrastructure.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseSettingsTest {

    @TempDir
    Path dir;

    private static DatabaseSettings from(Map<String, String> values) {
        return DatabaseSettings.from(values::get);
    }

    @Test
    void defaultsToEmbeddedModeInUserHome() {
        DatabaseSettings settings = from(Map.of());

        assertEquals(DatabaseSettings.Mode.EMBEDDED, settings.mode());
        assertTrue(settings.dataDir().endsWith(".sells"));
    }

    @Test
    void readsExternalSettings() {
        DatabaseSettings settings = from(Map.of(
                "sells.db.mode", "external",
                "sells.db.url", "jdbc:postgresql://example:5432/sells",
                "sells.db.username", "app",
                "sells.db.password", "secret"));

        assertEquals(DatabaseSettings.Mode.EXTERNAL, settings.mode());
        assertEquals("jdbc:postgresql://example:5432/sells", settings.url());
        assertEquals("app", settings.username());
        assertEquals("secret", settings.password());
    }

    @Test
    void externalModeRequiresUrl() {
        assertThrows(IllegalArgumentException.class, () -> from(Map.of("sells.db.mode", "external")));
    }

    @Test
    void rejectsUnknownMode() {
        assertThrows(IllegalArgumentException.class, () -> from(Map.of("sells.db.mode", "sqlite")));
    }

    @Test
    void usesConfiguredDataDir() {
        DatabaseSettings settings = from(Map.of("sells.data.dir", dir.toString()));

        assertEquals(dir.toAbsolutePath(), settings.dataDir());
    }
}
