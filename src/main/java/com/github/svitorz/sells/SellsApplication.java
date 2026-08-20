package com.github.svitorz.sells;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SellsApplication extends Application {

  private ConfigurableApplicationContext springContext;

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void init() {
    springContext = new SpringApplicationBuilder(SellsApplication.class)
        .headless(false)
        .run(getParameters().getRaw().toArray(String[]::new));
  }

  @Override
  public void start(Stage stage) {
    Label title = new Label("Sells — JavaFX + Spring Boot");
    stage.setTitle("Sells");
    stage.setScene(new Scene(new StackPane(title), 800, 500));
    stage.show();
  }

  @Override
  public void stop() {
    if (springContext != null) {
      springContext.close();
    }
  }

}
