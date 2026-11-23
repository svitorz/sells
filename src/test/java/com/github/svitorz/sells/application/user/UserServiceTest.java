package com.github.svitorz.sells.application.user;

import com.github.svitorz.sells.application.common.AccessDeniedException;
import com.github.svitorz.sells.application.common.AuthenticationException;
import com.github.svitorz.sells.application.fakes.ApplicationTestFixture;
import com.github.svitorz.sells.application.session.AuthenticatedUser;
import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.user.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceTest {

    private static final String WRONG_LOGIN_MESSAGE = "Usuário ou senha inválidos.";

    private final ApplicationTestFixture f = new ApplicationTestFixture();

    private static NewUserCommand user(String name, String email, String username, String password, Role role) {
        return new NewUserCommand(name, email, username, password, role, null);
    }

    @Test
    void needsFirstAdminOnlyBeforeTheFirstAdminExists() {
        assertTrue(f.userService.needsFirstAdmin());

        f.loginAsAdmin();

        assertFalse(f.userService.needsFirstAdmin());
    }

    @Test
    void createFirstAdminForcesAdminRoleAndStartsSession() {
        AuthenticatedUser admin = f.userService.createFirstAdmin(
                user("Ana", "ana@example.com", "ana", "secret123", Role.USER));

        assertEquals(Role.ADMIN, admin.role());
        assertEquals(admin, f.session.current().orElseThrow());
    }

    @Test
    void secondFirstAdminIsRejected() {
        f.loginAsAdmin();

        DomainException e = assertThrows(DomainException.class,
                () -> f.userService.createFirstAdmin(user("Bia", "bia@example.com", "bia", "secret123", Role.ADMIN)));
        assertEquals("O administrador inicial já foi configurado.", e.getMessage());
    }

    @Test
    void wrongPasswordUnknownUserAndInactiveUserShareTheSameMessage() {
        f.loginAsAdmin();
        UserView operator = f.userService.createUser(
                user("Caixa", "caixa@example.com", "caixa", "secret123", Role.USER));
        f.userService.deactivateUser(operator.id());
        f.userService.logout();

        AuthenticationException wrongPassword = assertThrows(AuthenticationException.class,
                () -> f.userService.login("admin", "errada99"));
        AuthenticationException unknown = assertThrows(AuthenticationException.class,
                () -> f.userService.login("ninguem", "secret123"));
        AuthenticationException inactive = assertThrows(AuthenticationException.class,
                () -> f.userService.login("caixa", "secret123"));

        assertEquals(WRONG_LOGIN_MESSAGE, wrongPassword.getMessage());
        assertEquals(WRONG_LOGIN_MESSAGE, unknown.getMessage());
        assertEquals(WRONG_LOGIN_MESSAGE, inactive.getMessage());
    }

    @Test
    void loginIgnoresCaseAndSurroundingSpaces() {
        f.loginAsAdmin();
        f.userService.logout();

        AuthenticatedUser admin = f.userService.login("  Admin ", "secret123");

        assertEquals("admin", admin.username());
    }

    @Test
    void createUserNeedsAdminSession() {
        AccessDeniedException none = assertThrows(AccessDeniedException.class,
                () -> f.userService.createUser(user("Bia", "bia@example.com", "bia", "secret123", Role.USER)));
        assertEquals("Faça login para continuar.", none.getMessage());

        f.loginAsOperator();
        AccessDeniedException operator = assertThrows(AccessDeniedException.class,
                () -> f.userService.createUser(user("Bia", "bia@example.com", "bia", "secret123", Role.USER)));
        assertEquals("Somente administradores podem executar esta ação.", operator.getMessage());
    }

    @Test
    void shortPasswordIsRejected() {
        f.loginAsAdmin();

        DomainException e = assertThrows(DomainException.class,
                () -> f.userService.createUser(user("Bia", "bia@example.com", "bia", "12345", Role.USER)));
        assertEquals("A senha deve ter pelo menos 6 caracteres.", e.getMessage());
    }

    @Test
    void duplicateEmailAndUsernameAreRejectedEvenWhenTheExistingUserIsInactive() {
        f.loginAsAdmin();
        UserView ana = f.userService.createUser(user("Ana", "ana@example.com", "ana", "secret123", Role.USER));
        f.userService.deactivateUser(ana.id());

        DomainException email = assertThrows(DomainException.class,
                () -> f.userService.createUser(user("Outra", "ana@example.com", "outra", "secret123", Role.USER)));
        DomainException username = assertThrows(DomainException.class,
                () -> f.userService.createUser(user("Outra", "outra@example.com", "ana", "secret123", Role.USER)));

        assertEquals("Já existe um usuário com este e-mail.", email.getMessage());
        assertEquals("Já existe um usuário com este nome de usuário.", username.getMessage());
    }

    @Test
    void demotingTheLastActiveAdminIsRejectedButDemotingOneOfTwoIsAccepted() {
        AuthenticatedUser admin = f.loginAsAdmin();

        DomainException last = assertThrows(DomainException.class,
                () -> f.userService.updateUser(admin.id(),
                        new UpdateUserCommand("Administrador", "admin@example.com", "admin", Role.USER, null)));
        assertEquals("Não é possível remover o último administrador ativo.", last.getMessage());

        f.userService.createUser(user("Bia", "bia@example.com", "bia", "secret123", Role.ADMIN));
        UserView demoted = f.userService.updateUser(admin.id(),
                new UpdateUserCommand("Administrador", "admin@example.com", "admin", Role.USER, null));

        assertEquals(Role.USER, demoted.role());
    }

    @Test
    void adminCannotDeactivateHimself() {
        AuthenticatedUser admin = f.loginAsAdmin();

        DomainException e = assertThrows(DomainException.class,
                () -> f.userService.deactivateUser(admin.id()));
        assertEquals("Você não pode desativar o seu próprio usuário.", e.getMessage());
    }

    @Test
    void adminCanDeactivateAnotherAdminWhenTwoAreActive() {
        f.loginAsAdmin();
        UserView other = f.userService.createUser(user("Bia", "bia@example.com", "bia", "secret123", Role.ADMIN));

        f.userService.deactivateUser(other.id());

        assertFalse(f.userService.listUsers().stream()
                .filter(view -> view.id() == other.id())
                .findFirst().orElseThrow().active());
    }

    @Test
    void changedPasswordIsUsedByNextLogin() {
        f.loginAsAdmin();
        UserView operator = f.userService.createUser(
                user("Caixa", "caixa@example.com", "caixa", "secret123", Role.USER));

        f.userService.changePassword(operator.id(), "novasenha");
        f.userService.logout();

        assertThrows(AuthenticationException.class, () -> f.userService.login("caixa", "secret123"));
        assertEquals("caixa", f.userService.login("caixa", "novasenha").username());
    }
}
