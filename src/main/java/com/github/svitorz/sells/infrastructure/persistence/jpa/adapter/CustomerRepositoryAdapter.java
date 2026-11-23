package com.github.svitorz.sells.infrastructure.persistence.jpa.adapter;

import com.github.svitorz.sells.application.port.CustomerRepository;
import com.github.svitorz.sells.domain.customer.Customer;
import com.github.svitorz.sells.domain.shared.Texts;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.CustomerEntity;
import com.github.svitorz.sells.infrastructure.persistence.jpa.repository.CustomerJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional
public class CustomerRepositoryAdapter implements CustomerRepository {

    private final CustomerJpaRepository jpa;

    public CustomerRepositoryAdapter(CustomerJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Customer> findActiveById(long id) {
        return jpa.findByIdAndDeletedAtIsNull(id).map(CustomerMapper::toDomain);
    }

    @Override
    public List<Customer> search(String term, int limit) {
        Specification<CustomerEntity> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        if (term != null && !term.isBlank()) {
            String trimmed = term.trim();
            String like = "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
            String digits = Texts.digitsOnly(trimmed);
            Specification<CustomerEntity> match = (root, query, cb) -> {
                List<Predicate> options = new ArrayList<>();
                options.add(cb.like(cb.lower(root.get("name")), like));
                options.add(cb.like(root.get("phone"), "%" + trimmed + "%"));
                if (digits != null) {
                    options.add(cb.like(root.get("cpfCnpj"), digits + "%"));
                }
                return cb.or(options.toArray(Predicate[]::new));
            };
            spec = spec.and(match);
        }
        return jpa.findAll(spec, PageRequest.of(0, limit, Sort.by("name")))
                .getContent().stream().map(CustomerMapper::toDomain).toList();
    }

    @Override
    public boolean existsActiveByCpfCnpj(String cpfCnpj, Long excludingId) {
        return excludingId == null
                ? jpa.existsByCpfCnpjAndDeletedAtIsNull(cpfCnpj)
                : jpa.existsByCpfCnpjAndDeletedAtIsNullAndIdNot(cpfCnpj, excludingId);
    }

    @Override
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        for (CustomerEntity entity : jpa.findAllById(ids)) {
            names.put(entity.getId(), entity.getName());
        }
        return names;
    }

    @Override
    public Customer save(Customer customer) {
        CustomerEntity entity;
        if (customer.getId() == null) {
            entity = new CustomerEntity();
        } else {
            entity = jpa.findById(customer.getId())
                    .orElseThrow(() -> new IllegalStateException("Customer " + customer.getId() + " not found"));
        }
        CustomerMapper.copyToEntity(customer, entity);
        return CustomerMapper.toDomain(jpa.saveAndFlush(entity));
    }
}
