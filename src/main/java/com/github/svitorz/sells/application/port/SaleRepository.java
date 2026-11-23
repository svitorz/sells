package com.github.svitorz.sells.application.port;

import com.github.svitorz.sells.application.sale.SaleSearch;
import com.github.svitorz.sells.domain.sale.Sale;

import java.util.List;
import java.util.Optional;

public interface SaleRepository {

    Optional<Sale> findById(long id);

    /** Highest id with status DRAFT. */
    Optional<Sale> findLatestDraftByUser(long userId);

    /**
     * Sales matching every non-null criterion, ordered by date desc, id desc.
     * from/to are inclusive and compare the date part of sale.date.
     * A null status means COMPLETED or CANCELLED (drafts are never part of the history).
     */
    List<Sale> search(SaleSearch criteria, int limit);

    Sale save(Sale sale);

    void deleteById(long id);
}
