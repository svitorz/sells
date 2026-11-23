package com.github.svitorz.sells.domain.user;

import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.shared.Texts;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class User {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,}$");

    private final Long id;
    private String name;
    private String email;
    private String username;
    private String passwordHash;
    private Role role;
    private LocalDate birthdayDate;
    private boolean active;

    private User(Long id, String name, String email, String username, String passwordHash, Role role,
                 LocalDate birthdayDate, boolean active) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.birthdayDate = birthdayDate;
        this.active = active;
    }

    public static User create(String name, String email, String username, String passwordHash, Role role,
                              LocalDate birthdayDate) {
        requirePasswordHash(passwordHash);
        User user = new User(null, null, null, null, passwordHash, null, birthdayDate, true);
        user.updateProfile(name, email, username, role, birthdayDate);
        return user;
    }

    public static User restore(Long id, String name, String email, String username, String passwordHash,
                               Role role, LocalDate birthdayDate, boolean active) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(role, "role");
        return new User(id, name, email, username, passwordHash, role, birthdayDate, active);
    }

    public void updateProfile(String name, String email, String username, Role role, LocalDate birthdayDate) {
        String newName = Texts.required(name, "Nome", 100);
        String newEmail = normalizeEmail(email);
        String newUsername = normalizeUsername(username);
        if (role == null) {
            throw new DomainException("O campo Perfil é obrigatório.");
        }
        this.name = newName;
        this.email = newEmail;
        this.username = newUsername;
        this.role = role;
        this.birthdayDate = birthdayDate;
    }

    public void changePasswordHash(String passwordHash) {
        requirePasswordHash(passwordHash);
        this.passwordHash = passwordHash;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    private static void requirePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash is required");
        }
    }

    private static String normalizeEmail(String email) {
        String value = Texts.required(email, "E-mail", 100).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(value).matches()) {
            throw new DomainException("Informe um e-mail válido.");
        }
        return value;
    }

    private static String normalizeUsername(String username) {
        String value = Texts.required(username, "Usuário", 100).toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(value).matches()) {
            throw new DomainException("O usuário deve ter pelo menos 3 caracteres e usar apenas letras, "
                    + "números, ponto, hífen ou sublinhado.");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public LocalDate getBirthdayDate() {
        return birthdayDate;
    }

    public boolean isActive() {
        return active;
    }
}
