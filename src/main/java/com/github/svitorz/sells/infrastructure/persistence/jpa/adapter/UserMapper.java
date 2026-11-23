package com.github.svitorz.sells.infrastructure.persistence.jpa.adapter;

import com.github.svitorz.sells.domain.user.User;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.UserEntity;

final class UserMapper {

    private UserMapper() {
    }

    static User toDomain(UserEntity entity) {
        return User.restore(entity.getId(), entity.getName(), entity.getEmail(), entity.getUsername(),
                entity.getPassword(), entity.getRole(), entity.getBirthdayDate(), entity.isActive());
    }

    static void copyToEntity(User user, UserEntity entity) {
        entity.setName(user.getName());
        entity.setEmail(user.getEmail());
        entity.setUsername(user.getUsername());
        entity.setPassword(user.getPasswordHash());
        entity.setRole(user.getRole());
        entity.setBirthdayDate(user.getBirthdayDate());
        entity.setActive(user.isActive());
    }
}
