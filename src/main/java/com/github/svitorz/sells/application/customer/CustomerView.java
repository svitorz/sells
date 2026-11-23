package com.github.svitorz.sells.application.customer;

import java.time.LocalDate;

public record CustomerView(long id, String name, String phone, String email, LocalDate birthdayDate,
                           String cpfCnpj, AddressData address) {
}
