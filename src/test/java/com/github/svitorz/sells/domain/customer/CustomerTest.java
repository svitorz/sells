package com.github.svitorz.sells.domain.customer;

import com.github.svitorz.sells.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 10, 0);

    private static Customer create(String cpfCnpj, LocalDate birthdayDate, Address address) {
        return Customer.create("Ana", "11999990000", null, birthdayDate, cpfCnpj, address, NOW);
    }

    @Test
    void cpfKeepsOnlyDigits() {
        assertEquals("12345678901", create("123.456.789-01", null, null).getCpfCnpj());
    }

    @Test
    void cnpjKeepsOnlyDigits() {
        assertEquals("12345678000190", create("12.345.678/0001-90", null, null).getCpfCnpj());
    }

    @Test
    void cpfOrCnpjWithWrongLengthIsRejected() {
        DomainException e = assertThrows(DomainException.class, () -> create("123", null, null));
        assertEquals("O CPF deve ter 11 dígitos e o CNPJ, 14.", e.getMessage());
    }

    @Test
    void birthdayInTheFutureIsRejected() {
        LocalDate tomorrow = NOW.toLocalDate().plusDays(1);

        DomainException e = assertThrows(DomainException.class, () -> create(null, tomorrow, null));
        assertEquals("A data de nascimento não pode estar no futuro.", e.getMessage());
    }

    @Test
    void nullAddressBecomesEmptyAddress() {
        assertTrue(create(null, null, null).getAddress().isEmpty());
    }

    @Test
    void updateAfterDeleteIsRejected() {
        Customer customer = create(null, null, null);
        customer.delete(NOW);

        DomainException e = assertThrows(DomainException.class,
                () -> customer.update("Ana", "11999990000", null, null, null, null, NOW));
        assertEquals("Cliente excluído não pode ser alterado.", e.getMessage());
    }
}
