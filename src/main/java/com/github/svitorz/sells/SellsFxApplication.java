package com.github.svitorz.sells;

import com.github.svitorz.sells.infrastructure.database.DatabaseBootstrap;
import com.github.svitorz.sells.infrastructure.database.DatabaseSettings;
import com.github.svitorz.sells.infrastructure.database.InstanceAlreadyRunningException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX lifecycle: database, then Spring, then the window. Shutdown runs in reverse order.
 */
public class SellsFxApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(SellsFxApplication.class);

    private DatabaseBootstrap database;
    private ConfigurableApplicationContext context;
    private String startupError;

    @Override
    public void init() {
        try {
            database = DatabaseBootstrap.start(DatabaseSettings.fromEnvironment());
            context = new SpringApplicationBuilder(SellsApplication.class)
                    .headless(false)
                    .web(WebApplicationType.NONE)
                    .properties(database.springProperties())
                    .run(getParameters().getRaw().toArray(String[]::new));
        } catch (InstanceAlreadyRunningException e) {
            log.warn(e.getMessage());
            startupError = "O Sells já está aberto neste computador.";
            shutdown();
        } catch (RuntimeException e) {
            log.error("Startup failed", e);
            startupError = "Não foi possível iniciar o Sells.\n\n" + e.getMessage();
            shutdown();
        }
    }

    @Override
    public void start(Stage stage) {
        if (startupError != null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, startupError);
            alert.setHeaderText("Erro ao iniciar");
            alert.showAndWait();
            Platform.exit();
            return;
        }
        stage.setTitle("Sells");
        stage.setScene(new Scene(new StackPane(new Label("Sells")), 800, 500));
        stage.setOnCloseRequest(event -> Platform.exit());
        stage.show();
    }

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        if (context != null) {
            context.close();
            context = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
    }
}
