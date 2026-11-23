package com.github.svitorz.sells.infrastructure.persistence.jpa.repository;

import com.github.svitorz.sells.domain.user.Role;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    List<UserEntity> findAllByOrderByNameAsc();

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByActiveTrue();

    long countByRoleAndActiveTrue(Role role);
}
