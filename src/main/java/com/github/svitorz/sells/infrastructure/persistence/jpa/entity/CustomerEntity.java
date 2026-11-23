package com.github.svitorz.sells.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
@SuperBuilder
@SQLDelete(sql = "UPDATE customers SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor
@Getter
public class CustomerEntity extends AuditableEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    private String name;

    @Setter
    @Column(name = "email")
    private String email;

    @Setter
    private LocalDate birthdayDate;

    @Setter
    @Column(name = "cpf_cnpj", length = 14)
    private String cpfCnpj;

    @Setter
    private String phone;

    @Setter
    @Embedded
    private AddressEmbeddable address;

    @Builder.Default
    @OneToMany(mappedBy = "customer")
    private List<SaleEntity> sales = new ArrayList<>();
}
