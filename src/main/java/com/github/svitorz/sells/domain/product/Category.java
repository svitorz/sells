package com.github.svitorz.sells.domain.product;

import com.github.svitorz.sells.domain.shared.Texts;

import java.util.Objects;

public final class Category {

    private final Long id;
    private String name;
    private String description;

    private Category(Long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public static Category create(String name, String description) {
        return new Category(null, Texts.required(name, "Nome", 255),
                Texts.optional(description, "Descrição", 2000));
    }

    public static Category restore(Long id, String name, String description) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        return new Category(id, name, description);
    }

    public void update(String name, String description) {
        this.name = Texts.required(name, "Nome", 255);
        this.description = Texts.optional(description, "Descrição", 2000);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
