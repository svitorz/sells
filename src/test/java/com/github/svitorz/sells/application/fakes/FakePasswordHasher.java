package com.github.svitorz.sells.application.fakes;

import com.github.svitorz.sells.application.port.PasswordHasher;

public final class FakePasswordHasher implements PasswordHasher {

    @Override
    public String hash(String rawPassword) {
        return "hashed:" + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return hash(rawPassword).equals(hash);
    }
}
