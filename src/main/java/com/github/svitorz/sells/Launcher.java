package com.github.svitorz.sells;

import javafx.application.Application;

/**
 * Process entry point. It must not extend javafx.application.Application,
 * otherwise the JVM refuses to start JavaFX from the classpath.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(SellsFxApplication.class, args);
    }
}
