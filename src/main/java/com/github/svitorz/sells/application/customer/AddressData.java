package com.github.svitorz.sells.application.customer;

public record AddressData(String street, String number, String complement, String neighborhood,
                          String city, String state, String zipCode) {

    public static AddressData empty() {
        return new AddressData(null, null, null, null, null, null, null);
    }
}
