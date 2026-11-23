package com.github.svitorz.sells.application.fakes;

import com.github.svitorz.sells.application.port.CustomerRepository;
import com.github.svitorz.sells.domain.customer.Customer;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<Long, Customer> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    @Override
    public Optional<Customer> findActiveById(long id) {
        return Optional.ofNullable(store.get(id))
                .filter(customer -> !customer.isDeleted())
                .map(InMemoryCustomerRepository::copy);
    }

    @Override
    public List<Customer> search(String term, int limit) {
        String digits = term == null ? "" : term.replaceAll("\\D", "");
        return store.values().stream()
                .filter(customer -> !customer.isDeleted())
                .filter(customer -> term == null
                        || containsIgnoreCase(customer.getName(), term)
                        || customer.getPhone().contains(term)
                        || (!digits.isEmpty() && customer.getCpfCnpj() != null
                        && customer.getCpfCnpj().startsWith(digits)))
                .sorted(Comparator.comparing(Customer::getName))
                .limit(limit)
                .map(InMemoryCustomerRepository::copy)
                .toList();
    }

    @Override
    public boolean existsActiveByCpfCnpj(String cpfCnpj, Long excludingId) {
        return store.values().stream()
                .anyMatch(customer -> !customer.isDeleted()
                        && cpfCnpj.equals(customer.getCpfCnpj())
                        && !Objects.equals(customer.getId(), excludingId));
    }

    @Override
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        for (Long id : ids) {
            Customer customer = store.get(id);
            if (customer != null) {
                names.put(id, customer.getName());
            }
        }
        return names;
    }

    @Override
    public Customer save(Customer customer) {
        long id = customer.getId() == null ? sequence.getAndIncrement() : customer.getId();
        Customer stored = Customer.restore(id, customer.getName(), customer.getPhone(), customer.getEmail(),
                customer.getBirthdayDate(), customer.getCpfCnpj(), customer.getAddress(),
                customer.getCreatedAt(), customer.getUpdatedAt(), customer.getDeletedAt());
        store.put(id, stored);
        return copy(stored);
    }

    private static boolean containsIgnoreCase(String value, String term) {
        return value.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    private static Customer copy(Customer customer) {
        return Customer.restore(customer.getId(), customer.getName(), customer.getPhone(), customer.getEmail(),
                customer.getBirthdayDate(), customer.getCpfCnpj(), customer.getAddress(),
                customer.getCreatedAt(), customer.getUpdatedAt(), customer.getDeletedAt());
    }
}
