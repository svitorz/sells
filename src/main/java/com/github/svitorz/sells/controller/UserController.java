package com.github.svitorz.sells.controller;

import com.github.svitorz.sells.domain.User;
import com.github.svitorz.sells.repository.UserRepository;
import com.github.svitorz.sells.service.UserService;
import com.github.svitorz.sells.view.UserRegistrationView;

public class UserController {

    private final UserService userService;

    public UserController(UserRepository userRepository) {
        this.userService = new UserService(userRepository);
    }

    public UserRegistrationView createRegistrationView() {
        UserRegistrationView view = new UserRegistrationView();
        view.onRegister(() -> register(view));
        return view;
    }

    private void register(UserRegistrationView view) {
        if (!view.getPassword().equals(view.getConfirmation())) {
            view.showFeedback("As senhas informadas não coincidem.", true);
            return;
        }

        try {
            User user = userService.prepareUser(
                view.getName(), view.getEmail(), view.getPassword(), view.getRole());

            // Persistência propositalmente fora do escopo desta tela.
            view.showFeedback("Dados preenchidos com sucesso. O cadastro ainda não foi persistido.", false);
        } catch (IllegalArgumentException exception) {
            view.showFeedback(exception.getMessage(), true);
        }
    }
}
