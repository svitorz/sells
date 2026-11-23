package com.github.svitorz.sells.application.user;

import com.github.svitorz.sells.application.common.AuthenticationException;
import com.github.svitorz.sells.application.common.NotFoundException;
import com.github.svitorz.sells.application.port.PasswordHasher;
import com.github.svitorz.sells.application.port.TransactionRunner;
import com.github.svitorz.sells.application.port.UserRepository;
import com.github.svitorz.sells.application.session.AuthenticatedUser;
import com.github.svitorz.sells.application.session.SessionContext;
import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.user.Role;
import com.github.svitorz.sells.domain.user.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public final class UserService {

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final String LAST_ADMIN_MESSAGE = "Não é possível remover o último administrador ativo.";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final SessionContext session;
    private final TransactionRunner tx;

    public UserService(UserRepository users, PasswordHasher hasher, SessionContext session, TransactionRunner tx) {
        this.users = users;
        this.hasher = hasher;
        this.session = session;
        this.tx = tx;
    }

    public boolean needsFirstAdmin() {
        return tx.inTransaction(() -> !users.existsActiveUser());
    }

    public AuthenticatedUser createFirstAdmin(NewUserCommand cmd) {
        return tx.inTransaction(() -> {
            if (users.existsActiveUser()) {
                throw new DomainException("O administrador inicial já foi configurado.");
            }
            User saved = users.save(newUser(cmd.name(), cmd.email(), cmd.username(), cmd.password(),
                    Role.ADMIN, cmd.birthdayDate()));
            AuthenticatedUser authenticated = toAuthenticated(saved);
            session.start(authenticated);
            return authenticated;
        });
    }

    public AuthenticatedUser login(String username, String password) {
        String normalized = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return tx.inTransaction(() -> {
            User user = users.findByUsername(normalized)
                    .filter(User::isActive)
                    .filter(candidate -> password != null && hasher.matches(password, candidate.getPasswordHash()))
                    .orElseThrow(() -> new AuthenticationException("Usuário ou senha inválidos."));
            AuthenticatedUser authenticated = toAuthenticated(user);
            session.start(authenticated);
            return authenticated;
        });
    }

    public void logout() {
        session.end();
    }

    public List<UserView> listUsers() {
        session.requireAdmin();
        return tx.inTransaction(() -> users.findAll().stream().map(UserService::toView).toList());
    }

    public List<UserOption> listUserOptions() {
        session.require();
        return tx.inTransaction(() -> users.findAll().stream()
                .map(user -> new UserOption(user.getId(), user.getName()))
                .toList());
    }

    public UserView createUser(NewUserCommand cmd) {
        session.requireAdmin();
        return tx.inTransaction(() -> toView(users.save(newUser(cmd.name(), cmd.email(), cmd.username(),
                cmd.password(), cmd.role(), cmd.birthdayDate()))));
    }

    public UserView updateUser(long id, UpdateUserCommand cmd) {
        session.requireAdmin();
        return tx.inTransaction(() -> {
            User user = findUser(id);
            if (user.isAdmin() && user.isActive() && cmd.role() == Role.USER && users.countActiveAdmins() == 1) {
                throw new DomainException(LAST_ADMIN_MESSAGE);
            }
            user.updateProfile(cmd.name(), cmd.email(), cmd.username(), cmd.role(), cmd.birthdayDate());
            ensureUnique(user, id);
            return toView(users.save(user));
        });
    }

    public void changePassword(long id, String newPassword) {
        session.requireAdmin();
        tx.inTransaction(() -> {
            User user = findUser(id);
            validatePassword(newPassword);
            user.changePasswordHash(hasher.hash(newPassword));
            users.save(user);
        });
    }

    public void deactivateUser(long id) {
        AuthenticatedUser admin = session.requireAdmin();
        tx.inTransaction(() -> {
            if (id == admin.id()) {
                throw new DomainException("Você não pode desativar o seu próprio usuário.");
            }
            User user = findUser(id);
            if (user.isAdmin() && user.isActive() && users.countActiveAdmins() == 1) {
                throw new DomainException(LAST_ADMIN_MESSAGE);
            }
            user.deactivate();
            users.save(user);
        });
    }

    public void activateUser(long id) {
        session.requireAdmin();
        tx.inTransaction(() -> {
            User user = findUser(id);
            user.activate();
            users.save(user);
        });
    }

    /** Validation order: password, then User.create (formats), then uniqueness. */
    private User newUser(String name, String email, String username, String password, Role role,
                         LocalDate birthdayDate) {
        validatePassword(password);
        User user = User.create(name, email, username, hasher.hash(password), role, birthdayDate);
        ensureUnique(user, null);
        return user;
    }

    private void ensureUnique(User user, Long excludingId) {
        if (users.existsByEmail(user.getEmail(), excludingId)) {
            throw new DomainException("Já existe um usuário com este e-mail.");
        }
        if (users.existsByUsername(user.getUsername(), excludingId)) {
            throw new DomainException("Já existe um usuário com este nome de usuário.");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new DomainException("A senha deve ter pelo menos 6 caracteres.");
        }
    }

    private User findUser(long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }

    private static AuthenticatedUser toAuthenticated(User user) {
        return new AuthenticatedUser(user.getId(), user.getName(), user.getUsername(), user.getRole());
    }

    private static UserView toView(User user) {
        return new UserView(user.getId(), user.getName(), user.getEmail(), user.getUsername(), user.getRole(),
                user.getBirthdayDate(), user.isActive());
    }
}
