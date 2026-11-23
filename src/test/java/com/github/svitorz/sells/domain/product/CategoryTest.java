package com.github.svitorz.sells.domain.product;

import com.github.svitorz.sells.domain.shared.DomainException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CategoryTest {

    @Test
    void rejectsBlankName() {
        assertThrows(DomainException.class, () -> Category.create("   ", null));
    }

    @Test
    void blankDescriptionBecomesNull() {
        Category category = Category.create("Bebidas", "   ");

        assertEquals("Bebidas", category.getName());
        assertNull(category.getDescription());
    }
}
