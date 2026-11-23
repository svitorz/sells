package com.github.svitorz.sells.application.port;

import com.github.svitorz.sells.domain.product.Product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Optional<Product> findActiveById(long id);

    Optional<Product> findActiveByBarcode(String barcode);

    /**
     * Active products whose name contains term (case-insensitive) or whose barcode equals term.
     * Ordered by name. A null term or categoryId means no filter on that field.
     */
    List<Product> search(String term, Long categoryId, int limit);

    /** excludingId may be null. */
    boolean existsActiveByBarcode(String barcode, Long excludingId);

    /** Includes deleted products. */
    List<Product> findAllByIds(Collection<Long> ids);

    /** Same as findAllByIds, but locks the rows (SELECT ... FOR UPDATE), ordered by id. */
    List<Product> findAllByIdsForUpdate(Collection<Long> ids);

    Product save(Product product);
}
