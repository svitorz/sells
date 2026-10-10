package com.github.svitorz.sells.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
public class Customer extends Auditable{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @NotBlank
    private String name;

    @Setter
    @Email
    @Column(name = "email")
    private String email;

    @Setter
    private LocalDate birthdayDate;

    @Setter
    @Pattern(regexp = "^([0-9]{11}|[0-9]{14})$")
    @Column(name = "cpf_cnpj", length = 14)
    private String cpfCnpj;

    @NotBlank
    @Setter
    private String phone;

    @Setter
    @Embedded
    private Address address;

    @Builder.Default
    @OneToMany(mappedBy = "customer")
    private List<Sale> sales = new ArrayList<>();
}
