package com.github.svitorz.sells.view;

import com.github.svitorz.sells.domain.Role;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.IntConsumer;

public class UserRegistrationView extends BorderPane {

    private final TextField nameField = new TextField();
    private final TextField emailField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final PasswordField confirmationField = new PasswordField();
    private final ComboBox<Role> roleField = new ComboBox<>();
    private final Label feedbackLabel = new Label();
    private final Button registerButton = new Button("Cadastrar usuário");

    public UserRegistrationView() {
        getStyleClass().add("registration-page");
        setPadding(new Insets(32));

        Label eyebrow = new Label("GESTÃO DE USUÁRIOS");
        eyebrow.getStyleClass().add("eyebrow");

        Label title = new Label("Cadastrar usuário");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("Preencha os dados abaixo para criar um novo acesso ao sistema.");
        subtitle.getStyleClass().add("subtitle");

        VBox header = new VBox(8, eyebrow, title, subtitle);
        header.setMaxWidth(620);

        GridPane form = new GridPane();
        form.setHgap(18);
        form.setVgap(8);
        form.setMaxWidth(620);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(150);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(labelColumn, fieldColumn);

        nameField.setPromptText("Nome completo");
        emailField.setPromptText("nome@empresa.com");
        passwordField.setPromptText("Mínimo de 6 caracteres");
        confirmationField.setPromptText("Repita a senha");
        roleField.getItems().setAll(Role.values());
        roleField.setPromptText("Selecione um perfil");
        roleField.setMaxWidth(Double.MAX_VALUE);

        addRow(form, 0, "Nome", nameField);
        addRow(form, 1, "E-mail", emailField);
        addRow(form, 2, "Senha", passwordField);
        addRow(form, 3, "Confirmar senha", confirmationField);
        addRow(form, 4, "Perfil", roleField);

        feedbackLabel.setWrapText(true);
        feedbackLabel.getStyleClass().add("feedback");
        registerButton.getStyleClass().add("primary-button");
        registerButton.setDefaultButton(true);

        VBox card = new VBox(24, header, form, feedbackLabel, registerButton);
        card.getStyleClass().add("registration-card");
        card.setMaxWidth(700);
        card.setAlignment(Pos.TOP_LEFT);

        BorderPane.setAlignment(card, Pos.TOP_CENTER);
        setCenter(card);
    }

    private void addRow(GridPane form, int row, String labelText, Node input) {
        Label label = new Label(labelText);
        label.getStyleClass().add("field-label");
        form.add(label, 0, row);
        form.add(input, 1, row);
        GridPane.setHgrow(input, Priority.ALWAYS);
    }

    public String getName() {
        return nameField.getText();
    }

    public String getEmail() {
        return emailField.getText();
    }

    public String getPassword() {
        return passwordField.getText();
    }

    public String getConfirmation() {
        return confirmationField.getText();
    }

    public Role getRole() {
        return roleField.getValue();
    }

    public void onRegister(Runnable action) {
        registerButton.setOnAction(event -> action.run());
    }

    public void showFeedback(String message, boolean error) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("success", "error");
        feedbackLabel.getStyleClass().add(error ? "error" : "success");
    }
}
