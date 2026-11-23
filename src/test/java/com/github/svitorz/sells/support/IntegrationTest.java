package com.github.svitorz.sells.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for tests that need the full Spring context and a real database.
 * Every test starts with empty tables.
 */
@SpringBootTest
public abstract class IntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestDatabase::jdbcUrl);
        registry.add("spring.datasource.username", () -> TestDatabase.USERNAME);
        registry.add("spring.datasource.password", () -> TestDatabase.PASSWORD);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("truncate users, categories, products, product_category, customers, "
                + "sales, sale_items, stock_movements restart identity cascade");
    }
}
