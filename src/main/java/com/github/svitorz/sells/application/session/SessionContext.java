package com.github.svitorz.sells.application.session;

import com.github.svitorz.sells.application.common.AccessDeniedException;

import java.util.Optional;

/** The user logged in on this desktop. One instance per application. */
public final class SessionContext {

    private volatile AuthenticatedUser current;

    public Optional<AuthenticatedUser> current() {
        return Optional.ofNullable(current);
    }

    public AuthenticatedUser require() {
        AuthenticatedUser user = current;
        if (user == null) {
            throw new AccessDeniedException("Faça login para continuar.");
        }
        return user;
    }

    public AuthenticatedUser requireAdmin() {
        AuthenticatedUser user = require();
        if (!user.isAdmin()) {
            throw new AccessDeniedException("Somente administradores podem executar esta ação.");
        }
        return user;
    }

    public void start(AuthenticatedUser user) {
        this.current = user;
    }

    public void end() {
        this.current = null;
    }
}
