package com.github.svitorz.sells.application.user;

import com.github.svitorz.sells.domain.user.Role;

import java.time.LocalDate;

public record UserView(long id, String name, String email, String username, Role role, LocalDate birthdayDate,
                       boolean active) {
}
