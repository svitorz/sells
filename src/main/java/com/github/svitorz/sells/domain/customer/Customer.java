package com.github.svitorz.sells.domain.customer;

import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.shared.Texts;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class Customer {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final Long id;
    private String name;
    private String phone;
    private String email;
    private LocalDate birthdayDate;
    private String cpfCnpj;
    private Address address;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    private Customer(Long id, String name, String phone, String email, LocalDate birthdayDate, String cpfCnpj,
                     Address address, LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.birthdayDate = birthdayDate;
        this.cpfCnpj = cpfCnpj;
        this.address = address;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public static Customer create(String name, String phone, String email, LocalDate birthdayDate,
                                  String cpfCnpj, Address address, LocalDateTime now) {
        Objects.requireNonNull(now, "now");
        Customer customer = new Customer(null, null, null, null, null, null, null, now, now, null);
        customer.update(name, phone, email, birthdayDate, cpfCnpj, address, now);
        return customer;
    }

    public static Customer restore(Long id, String name, String phone, String email, LocalDate birthdayDate,
                                   String cpfCnpj, Address address, LocalDateTime createdAt,
                                   LocalDateTime updatedAt, LocalDateTime deletedAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        return new Customer(id, name, phone, email, birthdayDate, cpfCnpj,
                address == null ? Address.empty() : address, createdAt, updatedAt, deletedAt);
    }

    public void update(String name, String phone, String email, LocalDate birthdayDate,
                       String cpfCnpj, Address address, LocalDateTime now) {
        requireActive();
        String newName = Texts.required(name, "Nome", 255);
        String newPhone = Texts.required(phone, "Telefone", 20);
        String newEmail = normalizeEmail(Texts.optional(email, "E-mail", 255));
        if (birthdayDate != null && birthdayDate.isAfter(now.toLocalDate())) {
            throw new DomainException("A data de nascimento não pode estar no futuro.");
        }
        String newCpfCnpj = normalizeCpfCnpj(cpfCnpj);

        this.name = newName;
        this.phone = newPhone;
        this.email = newEmail;
        this.birthdayDate = birthdayDate;
        this.cpfCnpj = newCpfCnpj;
        this.address = address == null ? Address.empty() : address;
        this.updatedAt = now;
    }

    public void delete(LocalDateTime now) {
        requireActive();
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void requireActive() {
        if (deletedAt != null) {
            throw new DomainException("Cliente excluído não pode ser alterado.");
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String value = email.toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(value).matches()) {
            throw new DomainException("Informe um e-mail válido.");
        }
        return value;
    }

    private static String normalizeCpfCnpj(String cpfCnpj) {
        String digits = Texts.digitsOnly(cpfCnpj);
        if (digits != null && digits.length() != 11 && digits.length() != 14) {
            throw new DomainException("O CPF deve ter 11 dígitos e o CNPJ, 14.");
        }
        return digits;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public LocalDate getBirthdayDate() {
        return birthdayDate;
    }

    public String getCpfCnpj() {
        return cpfCnpj;
    }

    public Address getAddress() {
        return address;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
