package com.github.svitorz.sells.domain.customer;

import com.github.svitorz.sells.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddressTest {

    @Test
    void stateIsUppercased() {
        Address address = new Address(null, null, null, null, null, "sp", null);

        assertEquals("SP", address.state());
    }

    @Test
    void zipCodeKeepsOnlyDigits() {
        Address address = new Address(null, null, null, null, null, null, "01310-100");

        assertEquals("01310100", address.zipCode());
    }

    @Test
    void zipCodeWithoutEightDigitsIsRejected() {
        DomainException e = assertThrows(DomainException.class,
                () -> new Address(null, null, null, null, null, null, "123"));
        assertEquals("O CEP deve ter 8 dígitos.", e.getMessage());
    }

    @Test
    void stateWithDigitsIsRejected() {
        DomainException e = assertThrows(DomainException.class,
                () -> new Address(null, null, null, null, null, "S1", null));
        assertEquals("Informe a UF com 2 letras.", e.getMessage());
    }

    @Test
    void emptyAddressIsEmpty() {
        assertTrue(Address.empty().isEmpty());
    }
}
