package com.github.svitorz.sells.application.customer;

import com.github.svitorz.sells.application.common.NotFoundException;
import com.github.svitorz.sells.application.fakes.ApplicationTestFixture;
import com.github.svitorz.sells.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerServiceTest {

    private final ApplicationTestFixture f = new ApplicationTestFixture();

    private static CustomerCommand customer(String name, String phone, String cpfCnpj, AddressData address) {
        return new CustomerCommand(name, phone, null, null, cpfCnpj, address);
    }

    @Test
    void operatorCanCreateUpdateAndDeleteCustomers() {
        f.loginAsOperator();

        CustomerView created = f.customerService.create(customer("Maria", "11999990000", null, null));
        CustomerView updated = f.customerService.update(created.id(),
                customer("Maria Souza", "11999990001", null, null));
        assertEquals("Maria Souza", updated.name());

        f.customerService.delete(created.id());
        assertThrows(NotFoundException.class, () -> f.customerService.get(created.id()));
    }

    @Test
    void duplicateCpfIsRejectedUntilTheFirstIsDeleted() {
        f.loginAsOperator();
        CustomerView first = f.customerService.create(customer("Maria", "11999990000", "123.456.789-01", null));

        DomainException duplicate = assertThrows(DomainException.class,
                () -> f.customerService.create(customer("Outra", "11888880000", "12345678901", null)));
        assertEquals("Já existe um cliente ativo com este CPF/CNPJ.", duplicate.getMessage());

        f.customerService.delete(first.id());
        assertEquals("Outra", f.customerService.create(customer("Outra", "11888880000", "12345678901", null))
                .name());
    }

    @Test
    void searchFindsByNamePhoneAndCpfPrefixTypedWithPunctuation() {
        f.loginAsOperator();
        f.customerService.create(customer("Maria Souza", "11999990000", "123.456.789-01", null));

        assertEquals(List.of("Maria Souza"), names(f.customerService.search("souza")));
        assertEquals(List.of("Maria Souza"), names(f.customerService.search("99999")));
        assertEquals(List.of("Maria Souza"), names(f.customerService.search("123.456")));
        assertTrue(f.customerService.search("ninguém").isEmpty());
    }

    @Test
    void addressComesBackNormalized() {
        f.loginAsOperator();

        CustomerView created = f.customerService.create(customer("Maria", "11999990000", null,
                new AddressData("Rua A", "10", null, "Centro", "São Paulo", "sp", "01310-100")));

        assertEquals("SP", created.address().state());
        assertEquals("01310100", created.address().zipCode());
    }

    private static List<String> names(List<CustomerView> views) {
        return views.stream().map(CustomerView::name).toList();
    }
}
