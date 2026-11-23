package com.github.svitorz.sells.application.fakes;

import com.github.svitorz.sells.application.port.TransactionRunner;

import java.util.function.Supplier;

/** Runs the work directly. No rollback: a service that throws after a write leaves the write behind. */
public final class DirectTransactionRunner implements TransactionRunner {

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }
}
