package com.github.svitorz.sells.infrastructure.transaction;

import com.github.svitorz.sells.application.common.ConcurrentUpdateException;
import com.github.svitorz.sells.application.port.TransactionRunner;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate template;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        try {
            return template.execute(status -> work.get());
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrentUpdateException(
                    "Este registro foi alterado por outra operação. Feche e abra a tela novamente.");
        }
    }
}
