package com.github.svitorz.sells;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import com.github.svitorz.sells.controller.UserController;
import com.github.svitorz.sells.repository.UserRepository;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SellsApplication extends Application {

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage stage) {
    UserRepository userRepository = new UserRepository() {
      // Implementação de persistência será adicionada em outra etapa.
    };
    UserController controller = new UserController(userRepository);

    stage.setTitle("Sells");
    var view = controller.createRegistrationView();
    view.getStylesheets().add(getClass().getResource("/styles/user-registration.css").toExternalForm());
    stage.setScene(new Scene(view, 820, 650));
    stage.show();
  }

}
