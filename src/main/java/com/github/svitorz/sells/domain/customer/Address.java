package com.github.svitorz.sells.domain.customer;

import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.shared.Texts;

import java.util.Locale;

public record Address(String street, String number, String complement, String neighborhood,
                      String city, String state, String zipCode) {

    public Address {
        street = Texts.optional(street, "Rua", 150);
        number = Texts.optional(number, "Número", 20);
        complement = Texts.optional(complement, "Complemento", 100);
        neighborhood = Texts.optional(neighborhood, "Bairro", 100);
        city = Texts.optional(city, "Cidade", 100);
        state = normalizeState(state);
        zipCode = normalizeZipCode(zipCode);
    }

    public static Address empty() {
        return new Address(null, null, null, null, null, null, null);
    }

    public boolean isEmpty() {
        return street == null && number == null && complement == null && neighborhood == null
                && city == null && state == null && zipCode == null;
    }

    private static String normalizeState(String state) {
        String value = Texts.optional(state, "UF", 2);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (!upper.matches("^[A-Z]{2}$")) {
            throw new DomainException("Informe a UF com 2 letras.");
        }
        return upper;
    }

    private static String normalizeZipCode(String zipCode) {
        String digits = Texts.digitsOnly(zipCode);
        if (digits == null) {
            return null;
        }
        if (digits.length() != 8) {
            throw new DomainException("O CEP deve ter 8 dígitos.");
        }
        return digits;
    }
}
