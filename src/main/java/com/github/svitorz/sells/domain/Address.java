package com.github.svitorz.sells.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @NotBlank
    @Column(name = "street", nullable = false, length = 150)
    private String street;

    @NotBlank
    @Column(name = "number", nullable = false, length = 20)
    private String number;

    @Column(name = "complement", length = 100)
    private String complement;

    @NotBlank
    @Column(name = "neighborhood", nullable = false, length = 100)
    private String neighborhood;

    @NotBlank
    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @NotBlank
    @Column(name = "state", nullable = false, length = 2)
    private String state;

    @NotBlank
    @Column(name = "zip_code", nullable = false, length = 10)
    private String zipCode;
}
