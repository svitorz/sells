package com.github.svitorz.sells.infrastructure.persistence;

import com.github.svitorz.sells.domain.user.Role;
import com.github.svitorz.sells.domain.user.User;
import com.github.svitorz.sells.support.ServiceIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPersistenceTest extends ServiceIntegrationTest {

    @Test
    void roundTripKeepsEveryField() {
        User saved = users.save(User.create("Ana Souza", "ana@example.com", "ana", "hash-1", Role.ADMIN,
                LocalDate.of(1990, 5, 20)));

        User loaded = users.findById(saved.getId()).orElseThrow();

        assertEquals("Ana Souza", loaded.getName());
        assertEquals("ana@example.com", loaded.getEmail());
        assertEquals("ana", loaded.getUsername());
        assertEquals("hash-1", loaded.getPasswordHash());
        assertEquals(Role.ADMIN, loaded.getRole());
        assertEquals(LocalDate.of(1990, 5, 20), loaded.getBirthdayDate());
        assertTrue(loaded.isActive());
    }

    @Test
    void findByUsernameMatchesExactly() {
        users.save(User.create("Ana", "ana@example.com", "ana", "h", Role.USER, null));

        assertTrue(users.findByUsername("ana").isPresent());
        assertFalse(users.findByUsername("an").isPresent());
    }

    @Test
    void existsByEmailHonoursTheExcludedId() {
        User saved = users.save(User.create("Ana", "ana@example.com", "ana", "h", Role.USER, null));

        assertTrue(users.existsByEmail("ana@example.com", null));
        assertFalse(users.existsByEmail("ana@example.com", saved.getId()));
    }

    @Test
    void countActiveAdminsIgnoresInactiveUsers() {
        users.save(User.create("Admin A", "admina@example.com", "admina", "h", Role.ADMIN, null));
        User inactive = users.save(User.create("Admin B", "adminb@example.com", "adminb", "h", Role.ADMIN, null));
        inactive.deactivate();
        users.save(inactive);

        assertEquals(1, users.countActiveAdmins());
    }

    @Test
    void findNamesByIdsReturnsIdToName() {
        User ana = users.save(User.create("Ana", "ana@example.com", "ana", "h", Role.USER, null));

        assertEquals("Ana", users.findNamesByIds(List.of(ana.getId())).get(ana.getId()));
    }
}
