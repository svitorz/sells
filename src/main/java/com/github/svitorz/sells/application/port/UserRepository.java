package com.github.svitorz.sells.application.port;

import com.github.svitorz.sells.domain.user.User;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(long id);

    /** Exact match. The username is already lowercase. */
    Optional<User> findByUsername(String username);

    /** All users, active and inactive, ordered by name. */
    List<User> findAll();

    /** excludingId may be null. */
    boolean existsByEmail(String email, Long excludingId);

    /** excludingId may be null. */
    boolean existsByUsername(String username, Long excludingId);

    boolean existsActiveUser();

    long countActiveAdmins();

    Map<Long, String> findNamesByIds(Collection<Long> ids);

    /** Returns the saved state, with id. */
    User save(User user);
}
