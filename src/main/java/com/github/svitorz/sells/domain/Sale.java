package com.github.svitorz.sells.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Table(name = "sales")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime date;

    @NotNull
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SaleStatus status;

    //TODO: migrar para @ManyToOne quando a entidade Customer for modelada
    @NotNull
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

}
