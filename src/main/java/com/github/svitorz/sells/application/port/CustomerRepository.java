package com.github.svitorz.sells.application.port;

import com.github.svitorz.sells.domain.customer.Customer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CustomerRepository {

    Optional<Customer> findActiveById(long id);

    /**
     * Active customers whose name or phone contains term, or whose cpf_cnpj starts with the digits of term.
     * Ordered by name.
     */
    List<Customer> search(String term, int limit);

    /** excludingId may be null. */
    boolean existsActiveByCpfCnpj(String cpfCnpj, Long excludingId);

    /** Includes deleted customers. */
    Map<Long, String> findNamesByIds(Collection<Long> ids);

    Customer save(Customer customer);
}
