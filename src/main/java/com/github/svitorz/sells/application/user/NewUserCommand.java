package com.github.svitorz.sells.application.user;

import com.github.svitorz.sells.domain.user.Role;

import java.time.LocalDate;

public record NewUserCommand(String name, String email, String username, String password, Role role,
                             LocalDate birthdayDate) {
}
