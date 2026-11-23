package com.github.svitorz.sells.application.port;

import java.util.function.Supplier;

public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
