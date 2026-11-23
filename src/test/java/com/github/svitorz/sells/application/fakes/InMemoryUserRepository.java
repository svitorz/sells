package com.github.svitorz.sells.application.fakes;

import com.github.svitorz.sells.application.port.UserRepository;
import com.github.svitorz.sells.domain.user.User;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Stores copies, like a database does: callers never share state with the store. */
public final class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public Optional<User> findById(long id) {
        return Optional.ofNullable(store.get(id)).map(InMemoryUserRepository::copy);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return store.values().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst()
                .map(InMemoryUserRepository::copy);
    }

    @Override
    public List<User> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(User::getName))
                .map(InMemoryUserRepository::copy)
                .toList();
    }

    @Override
    public boolean existsByEmail(String email, Long excludingId) {
        return store.values().stream()
                .anyMatch(user -> user.getEmail().equals(email) && !Objects.equals(user.getId(), excludingId));
    }

    @Override
    public boolean existsByUsername(String username, Long excludingId) {
        return store.values().stream()
                .anyMatch(user -> user.getUsername().equals(username) && !Objects.equals(user.getId(), excludingId));
    }

    @Override
    public boolean existsActiveUser() {
        return store.values().stream().anyMatch(User::isActive);
    }

    @Override
    public long countActiveAdmins() {
        return store.values().stream().filter(user -> user.isAdmin() && user.isActive()).count();
    }

    @Override
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        for (Long id : ids) {
            User user = store.get(id);
            if (user != null) {
                names.put(id, user.getName());
            }
        }
        return names;
    }

    @Override
    public User save(User user) {
        long id = user.getId() == null ? sequence.getAndIncrement() : user.getId();
        User stored = User.restore(id, user.getName(), user.getEmail(), user.getUsername(),
                user.getPasswordHash(), user.getRole(), user.getBirthdayDate(), user.isActive());
        store.put(id, stored);
        return copy(stored);
    }

    private static User copy(User user) {
        return User.restore(user.getId(), user.getName(), user.getEmail(), user.getUsername(),
                user.getPasswordHash(), user.getRole(), user.getBirthdayDate(), user.isActive());
    }
}
