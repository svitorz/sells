package com.github.svitorz.sells.application.user;

import com.github.svitorz.sells.domain.user.Role;

import java.time.LocalDate;

public record UpdateUserCommand(String name, String email, String username, Role role, LocalDate birthdayDate) {
}
