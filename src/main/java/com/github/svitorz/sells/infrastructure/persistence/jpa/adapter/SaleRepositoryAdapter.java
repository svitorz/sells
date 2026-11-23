package com.github.svitorz.sells.infrastructure.persistence.jpa.adapter;

import com.github.svitorz.sells.application.common.ConcurrentUpdateException;
import com.github.svitorz.sells.application.port.SaleRepository;
import com.github.svitorz.sells.application.sale.SaleSearch;
import com.github.svitorz.sells.domain.product.ProductUnit;
import com.github.svitorz.sells.domain.sale.Sale;
import com.github.svitorz.sells.domain.sale.SaleStatus;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.SaleEntity;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.SaleItemEntity;
import com.github.svitorz.sells.infrastructure.persistence.jpa.repository.ProductJpaRepository;
import com.github.svitorz.sells.infrastructure.persistence.jpa.repository.SaleJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@Transactional
public class SaleRepositoryAdapter implements SaleRepository {

    private static final String CONFLICT_MESSAGE =
            "Este registro foi alterado por outra operação. Feche e abra a tela novamente.";

    private final SaleJpaRepository sales;
    private final ProductJpaRepository products;

    public SaleRepositoryAdapter(SaleJpaRepository sales, ProductJpaRepository products) {
        this.sales = sales;
        this.products = products;
    }

    @Override
    public Optional<Sale> findById(long id) {
        return sales.findById(id).map(entity -> toDomain(List.of(entity)).get(0));
    }

    @Override
    public Optional<Sale> findLatestDraftByUser(long userId) {
        return sales.findFirstByUserIdAndStatusOrderByIdDesc(userId, SaleStatus.DRAFT)
                .map(entity -> toDomain(List.of(entity)).get(0));
    }

    @Override
    public List<Sale> search(SaleSearch criteria, int limit) {
        Sort order = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"));
        List<SaleEntity> found = sales.findAll(matching(criteria), PageRequest.of(0, limit, order)).getContent();
        return toDomain(found);
    }

    @Override
    public Sale save(Sale sale) {
        SaleEntity entity;
        if (sale.getId() == null) {
            entity = new SaleEntity();
        } else {
            entity = sales.findById(sale.getId())
                    .orElseThrow(() -> new IllegalStateException("Sale " + sale.getId() + " not found"));
            if (!Objects.equals(entity.getVersion(), sale.getVersion())) {
                throw new ConcurrentUpdateException(CONFLICT_MESSAGE);
            }
        }
        SaleMapper.copyToEntity(sale, entity);
        return toDomain(List.of(sales.saveAndFlush(entity))).get(0);
    }

    @Override
    public void deleteById(long id) {
        sales.deleteById(id);
    }

    /** Converts sales in one query for all their products, including deleted ones (history). */
    private List<Sale> toDomain(List<SaleEntity> entities) {
        Set<Long> productIds = entities.stream()
                .flatMap(entity -> entity.getItems().stream())
                .map(SaleItemEntity::getProductId)
                .collect(Collectors.toSet());
        Map<Long, ProductUnit> units = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Object[] row : products.findIdAndUnitByIdIn(productIds)) {
                units.put((Long) row[0], (ProductUnit) row[1]);
            }
        }
        return entities.stream().map(entity -> SaleMapper.toDomain(entity, units)).toList();
    }

    private static Specification<SaleEntity> matching(SaleSearch criteria) {
        Specification<SaleEntity> spec = (root, query, cb) -> criteria.status() == null
                ? root.get("status").in(SaleStatus.COMPLETED, SaleStatus.CANCELLED)
                : cb.equal(root.get("status"), criteria.status());
        if (criteria.from() != null) {
            LocalDateTime start = criteria.from().atStartOfDay();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("date"), start));
        }
        if (criteria.to() != null) {
            LocalDateTime end = criteria.to().plusDays(1).atStartOfDay();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("date"), end));
        }
        if (criteria.customerId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("customerId"), criteria.customerId()));
        }
        if (criteria.userId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("userId"), criteria.userId()));
        }
        return spec;
    }
}
