package com.github.svitorz.sells.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(name = "products")
@SQLDelete(sql = "UPDATE products SET deleted_at = NOW () WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Product extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "barcode", length = 50)
    private String barcode;

    @NotNull
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false, length = 2)
    private ProductUnit unit;

    @NotBlank
    @Column(columnDefinition = "TEXT", name = "description", nullable = false)
    private String description;

    @NotNull
    @DecimalMin("0")
    @Column(name = "stock", nullable = false, precision = 10, scale = 3)
    private BigDecimal stock;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    @ManyToMany
    @JoinTable(
            name = "product_category",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

}
