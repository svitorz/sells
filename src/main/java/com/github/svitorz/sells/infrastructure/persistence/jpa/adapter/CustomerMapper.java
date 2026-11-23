package com.github.svitorz.sells.infrastructure.persistence.jpa.adapter;

import com.github.svitorz.sells.domain.customer.Address;
import com.github.svitorz.sells.domain.customer.Customer;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.AddressEmbeddable;
import com.github.svitorz.sells.infrastructure.persistence.jpa.entity.CustomerEntity;

final class CustomerMapper {

    private CustomerMapper() {
    }

    static Customer toDomain(CustomerEntity entity) {
        AddressEmbeddable stored = entity.getAddress();
        Address address = stored == null
                ? Address.empty()
                : new Address(stored.getStreet(), stored.getNumber(), stored.getComplement(),
                stored.getNeighborhood(), stored.getCity(), stored.getState(), stored.getZipCode());
        return Customer.restore(entity.getId(), entity.getName(), entity.getPhone(), entity.getEmail(),
                entity.getBirthdayDate(), entity.getCpfCnpj(), address,
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getDeletedAt());
    }

    static void copyToEntity(Customer customer, CustomerEntity entity) {
        Address address = customer.getAddress();
        AddressEmbeddable stored = new AddressEmbeddable();
        stored.setStreet(address.street());
        stored.setNumber(address.number());
        stored.setComplement(address.complement());
        stored.setNeighborhood(address.neighborhood());
        stored.setCity(address.city());
        stored.setState(address.state());
        stored.setZipCode(address.zipCode());

        entity.setName(customer.getName());
        entity.setPhone(customer.getPhone());
        entity.setEmail(customer.getEmail());
        entity.setBirthdayDate(customer.getBirthdayDate());
        entity.setCpfCnpj(customer.getCpfCnpj());
        entity.setAddress(stored);
        entity.setCreatedAt(customer.getCreatedAt());
        entity.setUpdatedAt(customer.getUpdatedAt());
        entity.setDeletedAt(customer.getDeletedAt());
    }
}
