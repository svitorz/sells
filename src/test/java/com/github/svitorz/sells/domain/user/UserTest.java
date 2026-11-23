package com.github.svitorz.sells.domain.user;

import com.github.svitorz.sells.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserTest {

    @Test
    void createTrimsAndLowercasesEmailAndUsername() {
        User user = User.create("  Ana  ", " ANA@Example.com ", " Ana.Maria ", "hash", Role.USER, null);

        assertEquals("Ana", user.getName());
        assertEquals("ana@example.com", user.getEmail());
        assertEquals("ana.maria", user.getUsername());
        assertTrue(user.isActive());
    }

    @Test
    void rejectsEmailWithoutAt() {
        DomainException e = assertThrows(DomainException.class,
                () -> User.create("Ana", "ana.example.com", "ana", "hash", Role.USER, null));
        assertEquals("Informe um e-mail válido.", e.getMessage());
    }

    @Test
    void rejectsUsernameShorterThanThreeCharacters() {
        DomainException e = assertThrows(DomainException.class,
                () -> User.create("Ana", "ana@example.com", "ab", "hash", Role.USER, null));
        assertEquals("O usuário deve ter pelo menos 3 caracteres e usar apenas letras, números, "
                + "ponto, hífen ou sublinhado.", e.getMessage());
    }

    @Test
    void rejectsUsernameWithSpace() {
        assertThrows(DomainException.class,
                () -> User.create("Ana", "ana@example.com", "ana maria", "hash", Role.USER, null));
    }

    @Test
    void rejectsBlankName() {
        DomainException e = assertThrows(DomainException.class,
                () -> User.create("   ", "ana@example.com", "ana", "hash", Role.USER, null));
        assertEquals("O campo Nome é obrigatório.", e.getMessage());
    }

    @Test
    void rejectsNameLongerThanOneHundredCharacters() {
        String name = "a".repeat(101);
        assertThrows(DomainException.class,
                () -> User.create(name, "ana@example.com", "ana", "hash", Role.USER, null));
    }

    @Test
    void deactivateAndActivateToggleActiveFlag() {
        User user = User.create("Ana", "ana@example.com", "ana", "hash", Role.USER, null);

        user.deactivate();
        assertFalse(user.isActive());
        user.activate();
        assertTrue(user.isActive());
    }

    @Test
    void isAdminReflectsRole() {
        assertTrue(User.create("Ana", "ana@example.com", "ana", "hash", Role.ADMIN, null).isAdmin());
        assertFalse(User.create("Bia", "bia@example.com", "bia", "hash", Role.USER, null).isAdmin());
    }
}
