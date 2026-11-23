package com.github.svitorz.sells.application.session;

import com.github.svitorz.sells.domain.user.Role;

public record AuthenticatedUser(long id, String name, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
