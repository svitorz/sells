package com.github.svitorz.sells.domain.product;

import com.github.svitorz.sells.domain.shared.Decimals;
import com.github.svitorz.sells.domain.shared.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum ProductUnit {
    UN(0, "Produtos vendidos por unidade aceitam apenas quantidades inteiras."),
    KG(3, "Produtos vendidos por quilo aceitam no máximo 3 casas decimais.");

    private final int maxDecimals;
    private final String decimalsMessage;

    ProductUnit(int maxDecimals, String decimalsMessage) {
        this.maxDecimals = maxDecimals;
        this.decimalsMessage = decimalsMessage;
    }

    /** A quantity being sold or received: must be greater than zero. */
    public BigDecimal quantity(BigDecimal value) {
        BigDecimal normalized = normalize(value);
        if (normalized.signum() <= 0) {
            throw new DomainException("A quantidade deve ser maior que zero.");
        }
        return normalized;
    }

    /** A stock balance: must be zero or greater. */
    public BigDecimal stock(BigDecimal value) {
        BigDecimal normalized = normalize(value);
        if (normalized.signum() < 0) {
            throw new DomainException("O estoque não pode ser negativo.");
        }
        return normalized;
    }

    /** A stock difference (adjustment): positive or negative, never zero. */
    public BigDecimal delta(BigDecimal value) {
        BigDecimal normalized = normalize(value);
        if (normalized.signum() == 0) {
            throw new DomainException("A quantidade deve ser diferente de zero.");
        }
        return normalized;
    }

    private BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            throw new DomainException("Informe a quantidade.");
        }
        if (Decimals.decimalPlaces(value) > maxDecimals) {
            throw new DomainException(decimalsMessage);
        }
        if (value.abs().compareTo(Decimals.MAX_QUANTITY) > 0) {
            throw new DomainException("A quantidade excede o máximo permitido.");
        }
        return value.setScale(3, RoundingMode.UNNECESSARY);
    }
}
