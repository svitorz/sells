package com.github.svitorz.sells.application.customer;

import java.time.LocalDate;

public record CustomerCommand(String name, String phone, String email, LocalDate birthdayDate,
                              String cpfCnpj, AddressData address) {
}
