package com.github.svitorz.sells.infrastructure.persistence.jpa.repository;

import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Long>,
        JpaSpecificationExecutor<CustomerEntity> {

    Optional<CustomerEntity> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCpfCnpjAndDeletedAtIsNull(String cpfCnpj);

    boolean existsByCpfCnpjAndDeletedAtIsNullAndIdNot(String cpfCnpj, Long id);
}
