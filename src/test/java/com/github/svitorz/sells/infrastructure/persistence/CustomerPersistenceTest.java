package com.github.svitorz.sells.infrastructure.persistence;

import com.github.svitorz.sells.domain.customer.Address;
import com.github.svitorz.sells.domain.customer.Customer;
import com.github.svitorz.sells.support.ServiceIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerPersistenceTest extends ServiceIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 10, 0);

    @Test
    void roundTripKeepsFullAddress() {
        Address address = new Address("Rua A", "10", "Apto 2", "Centro", "São Paulo", "SP", "01310100");
        Customer saved = customers.save(Customer.create("Maria Souza", "11999990000", "maria@example.com",
                LocalDate.of(1985, 3, 10), "123.456.789-01", address, NOW));

        Customer loaded = customers.findActiveById(saved.getId()).orElseThrow();

        assertEquals("Maria Souza", loaded.getName());
        assertEquals("maria@example.com", loaded.getEmail());
        assertEquals(LocalDate.of(1985, 3, 10), loaded.getBirthdayDate());
        assertEquals("12345678901", loaded.getCpfCnpj());
        assertEquals(address, loaded.getAddress());
    }

    @Test
    void emptyAddressRoundTrips() {
        Customer saved = customers.save(Customer.create("Maria", "11999990000", null, null, null, null, NOW));

        assertTrue(customers.findActiveById(saved.getId()).orElseThrow().getAddress().isEmpty());
    }

    @Test
    void searchFindsByNamePhoneAndCpfPrefixTypedWithPunctuation() {
        customers.save(Customer.create("Maria Souza", "11999990000", null, null, "123.456.789-01", null, NOW));

        assertEquals(1, customers.search("souza", 50).size());
        assertEquals(1, customers.search("99999", 50).size());
        assertEquals(1, customers.search("123.456", 50).size());
    }

    @Test
    void deletedCustomerLeavesSearch() {
        Customer saved = customers.save(Customer.create("Maria", "11999990000", null, null, null, null, NOW));
        Customer deleted = customers.findActiveById(saved.getId()).orElseThrow();
        deleted.delete(NOW);
        customers.save(deleted);

        assertTrue(customers.search("maria", 50).isEmpty());
    }
}
