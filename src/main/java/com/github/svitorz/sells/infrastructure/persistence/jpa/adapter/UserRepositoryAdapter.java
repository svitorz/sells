package com.github.svitorz.sells.infrastructure.persistence.jpa.adapter;

import com.github.svitorz.sells.application.port.UserRepository;
import com.github.svitorz.sells.domain.user.Role;
import com.github.svitorz.sells.domain.user.User;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.UserEntity;
import com.github.svitorz.sells.infrastructure.persistence.jpa.repository.UserJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    public UserRepositoryAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findById(long id) {
        return jpa.findById(id).map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username).map(UserMapper::toDomain);
    }

    @Override
    public List<User> findAll() {
        return jpa.findAllByOrderByNameAsc().stream().map(UserMapper::toDomain).toList();
    }

    @Override
    public boolean existsByEmail(String email, Long excludingId) {
        return excludingId == null ? jpa.existsByEmail(email) : jpa.existsByEmailAndIdNot(email, excludingId);
    }

    @Override
    public boolean existsByUsername(String username, Long excludingId) {
        return excludingId == null
                ? jpa.existsByUsername(username)
                : jpa.existsByUsernameAndIdNot(username, excludingId);
    }

    @Override
    public boolean existsActiveUser() {
        return jpa.existsByActiveTrue();
    }

    @Override
    public long countActiveAdmins() {
        return jpa.countByRoleAndActiveTrue(Role.ADMIN);
    }

    @Override
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        for (UserEntity entity : jpa.findAllById(ids)) {
            names.put(entity.getId(), entity.getName());
        }
        return names;
    }

    @Override
    public User save(User user) {
        UserEntity entity;
        if (user.getId() == null) {
            entity = new UserEntity();
        } else {
            entity = jpa.findById(user.getId())
                    .orElseThrow(() -> new IllegalStateException("User " + user.getId() + " not found"));
        }
        UserMapper.copyToEntity(user, entity);
        return UserMapper.toDomain(jpa.saveAndFlush(entity));
    }
}
