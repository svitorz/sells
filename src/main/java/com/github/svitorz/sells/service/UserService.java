package com.github.svitorz.sells.service;

import com.github.svitorz.sells.domain.Role;
import com.github.svitorz.sells.domain.User;
import com.github.svitorz.sells.repository.UserRepository;

/**
 * Application rules for users. Persistence is intentionally not implemented
 * yet; this service only prepares and validates data from the form.
 */
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User prepareUser(String name, String email, String password, Role role) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Informe o nome do usuário.");
        }
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("A senha deve ter pelo menos 6 caracteres.");
        }
        if (role == null) {
            throw new IllegalArgumentException("Selecione um perfil.");
        }

        return new User(name.trim(), email.trim(), password, role);
    }
}
