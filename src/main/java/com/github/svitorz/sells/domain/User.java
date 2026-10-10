package com.github.svitorz.sells.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@Table(name = "users")
public class User {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id" )
    private Long id;

    @NotBlank
    @Column(length = 100, nullable = false)
    private String name;

    @NotBlank
    @Email
    @Column(length = 100, nullable = false, unique = true)
    private String email;

    @NotBlank
    @Column(length = 100, nullable = false, unique = true)
    private String username;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @NotBlank
    @Column(length = 255, nullable = false)
    private String password;

    private LocalDate birthdayDate;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "user")
    private List<Sale> sales = new ArrayList<>();
}
