package com.github.svitorz.sells.application.customer;

import com.github.svitorz.sells.application.common.NotFoundException;
import com.github.svitorz.sells.application.port.CustomerRepository;
import com.github.svitorz.sells.application.port.TransactionRunner;
import com.github.svitorz.sells.application.session.SessionContext;
import com.github.svitorz.sells.domain.customer.Address;
import com.github.svitorz.sells.domain.customer.Customer;
import com.github.svitorz.sells.domain.shared.DomainException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Every method requires only a session: operators also manage customers (UC07). */
public final class CustomerService {

    private static final int SEARCH_LIMIT = 200;

    private final CustomerRepository customers;
    private final SessionContext session;
    private final TransactionRunner tx;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, SessionContext session, TransactionRunner tx,
                           Clock clock) {
        this.customers = customers;
        this.session = session;
        this.tx = tx;
        this.clock = clock;
    }

    public List<CustomerView> search(String term) {
        session.require();
        String clean = term == null || term.isBlank() ? null : term.trim();
        return tx.inTransaction(() -> customers.search(clean, SEARCH_LIMIT).stream()
                .map(CustomerService::toView)
                .toList());
    }

    public CustomerView get(long id) {
        session.require();
        return tx.inTransaction(() -> toView(findActive(id)));
    }

    public CustomerView create(CustomerCommand cmd) {
        session.require();
        return tx.inTransaction(() -> {
            Customer customer = Customer.create(cmd.name(), cmd.phone(), cmd.email(), cmd.birthdayDate(),
                    cmd.cpfCnpj(), toAddress(cmd.address()), now());
            checkCpfCnpjUnique(customer.getCpfCnpj(), null);
            return toView(customers.save(customer));
        });
    }

    public CustomerView update(long id, CustomerCommand cmd) {
        session.require();
        return tx.inTransaction(() -> {
            Customer customer = findActive(id);
            customer.update(cmd.name(), cmd.phone(), cmd.email(), cmd.birthdayDate(), cmd.cpfCnpj(),
                    toAddress(cmd.address()), now());
            checkCpfCnpjUnique(customer.getCpfCnpj(), id);
            return toView(customers.save(customer));
        });
    }

    public void delete(long id) {
        session.require();
        tx.inTransaction(() -> {
            Customer customer = findActive(id);
            customer.delete(now());
            customers.save(customer);
        });
    }

    private Customer findActive(long id) {
        return customers.findActiveById(id).orElseThrow(() -> new NotFoundException("Cliente não encontrado."));
    }

    private void checkCpfCnpjUnique(String cpfCnpj, Long excludingId) {
        if (cpfCnpj != null && customers.existsActiveByCpfCnpj(cpfCnpj, excludingId)) {
            throw new DomainException("Já existe um cliente ativo com este CPF/CNPJ.");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static Address toAddress(AddressData data) {
        if (data == null) {
            return Address.empty();
        }
        return new Address(data.street(), data.number(), data.complement(), data.neighborhood(),
                data.city(), data.state(), data.zipCode());
    }

    private static CustomerView toView(Customer customer) {
        Address address = customer.getAddress();
        return new CustomerView(customer.getId(), customer.getName(), customer.getPhone(), customer.getEmail(),
                customer.getBirthdayDate(), customer.getCpfCnpj(),
                new AddressData(address.street(), address.number(), address.complement(), address.neighborhood(),
                        address.city(), address.state(), address.zipCode()));
    }
}
