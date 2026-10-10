package com.github.svitorz.sells.persistence;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaConstraintsTest {

    private static EmbeddedPostgres postgres;
    private static DataSource dataSource;

    @BeforeAll
    static void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.builder()
                .setDataDirectory(Path.of("target", "schema-test-pgdata"))
                .setCleanDataDirectory(true)
                .setRegisterShutdownHook(false)
                .start();
        try (Connection c = postgres.getPostgresDatabase().getConnection(); Statement s = c.createStatement()) {
            s.execute("create database sells");
        }
        dataSource = postgres.getDatabase("postgres", "sells");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        postgres.close();
    }

    @BeforeEach
    void cleanTables() throws Exception {
        execute("truncate users, categories, products, product_category, customers, sales, sale_items, stock_movements restart identity cascade");
    }

    @Test
    void rejectsNegativeStock() throws Exception {
        assertRejected("insert into products (name, price, unit, description, stock) values ('Arroz', 10, 'KG', 'Tipo 1', -1)");
    }

    @Test
    void rejectsUnknownProductUnit() throws Exception {
        assertRejected("insert into products (name, price, unit, description, stock) values ('Arroz', 10, 'G', 'Tipo 1', 1)");
    }

    @Test
    void allowsFractionalStockForKilogramProducts() throws Exception {
        assertAccepted("insert into products (name, price, unit, description, stock) values ('Queijo', 40, 'KG', 'Minas', 2.375)");
    }

    @Test
    void barcodeIsUniqueOnlyAmongActiveProducts() throws Exception {
        long id = insertProduct("7891000100103");
        assertRejected("insert into products (name, price, unit, description, stock, barcode) values ('Outro', 1, 'UN', 'd', 1, '7891000100103')");

        execute("update products set deleted_at = now() where id = ?", id);
        assertAccepted("insert into products (name, price, unit, description, stock, barcode) values ('Novo', 1, 'UN', 'd', 1, '7891000100103')");
    }

    @Test
    void emailAndUsernameAreAlwaysUnique() throws Exception {
        long id = insertUser("ana@example.com", "ana");
        assertRejected("insert into users (name, email, username, password, role) values ('Outra', 'ana@example.com', 'outra', 'x', 'USER')");
        assertRejected("insert into users (name, email, username, password, role) values ('Outra', 'outra@example.com', 'ana', 'x', 'USER')");
        assertDoesNotThrow(() -> execute("update users set active = false where id = ?", id));
    }

    @Test
    void userIsActiveByDefault() throws Exception {
        long id = insertUser("ana@example.com", "ana");
        assertEquals("true", query("select case when active then 'true' else 'false' end from users where id = ?", id));
    }

    @Test
    void validatesCpfAndCnpjFormat() throws Exception {
        assertAccepted("insert into customers (name, phone, cpf_cnpj) values ('Ana', '11999990000', '12345678901')");
        assertAccepted("insert into customers (name, phone, cpf_cnpj) values ('Loja', '11999990001', '12345678000190')");
        assertAccepted("insert into customers (name, phone) values ('Sem documento', '11999990002')");
        assertRejected("insert into customers (name, phone, cpf_cnpj) values ('Ruim', '11999990003', '123')");
        assertRejected("insert into customers (name, phone, cpf_cnpj) values ('Ruim', '11999990004', '123.456.789-01')");
    }

    @Test
    void cancelledSaleRequiresCancellationDetails() throws Exception {
        long user = insertUser("ana@example.com", "ana");
        assertRejected("insert into sales (date, subtotal, status, user_id, cancelled_at, cancelled_by_user_id, cancel_reason) values (now(), 0, 'CANCELLED', ?, now(), ?, null)", user, user);
        assertRejected("insert into sales (date, subtotal, status, user_id, cancelled_at, cancelled_by_user_id, cancel_reason) values (now(), 0, 'CANCELLED', ?, now(), ?, '   ')", user, user);
        assertRejected("insert into sales (date, subtotal, status, user_id) values (now(), 0, 'CANCELLED', ?)", user);
        assertAccepted("insert into sales (date, subtotal, status, user_id, cancelled_at, cancelled_by_user_id, cancel_reason) values (now(), 0, 'CANCELLED', ?, now(), ?, 'Erro de cadastro')", user, user);
    }

    @Test
    void saleStatusIsRestricted() throws Exception {
        long user = insertUser("ana@example.com", "ana");
        assertRejected("insert into sales (date, subtotal, status, user_id) values (now(), 0, 'PAID', ?)", user);
    }

    @Test
    void saleItemQuantityMustBePositiveAndProductUniquePerSale() throws Exception {
        long user = insertUser("ana@example.com", "ana");
        long product = insertProduct(null);
        long sale = insertSale(user);

        assertRejected("insert into sale_items (product_id, quantity, unit_price, sale_id) values (?, 0, 10, ?)", product, sale);
        assertRejected("insert into sale_items (product_id, quantity, unit_price, sale_id) values (?, 1, -1, ?)", product, sale);
        assertAccepted("insert into sale_items (product_id, quantity, unit_price, sale_id) values (?, 0.250, 10, ?)", product, sale);
        assertRejected("insert into sale_items (product_id, quantity, unit_price, sale_id) values (?, 1, 10, ?)", product, sale);
    }

    @Test
    void stockMovementQuantityMatchesMovementType() throws Exception {
        long user = insertUser("ana@example.com", "ana");
        long product = insertProduct(null);
        long sale = insertSale(user);

        assertAccepted("insert into stock_movements (product_id, type, quantity, user_id, created_at) values (?, 'ENTRY', 5, ?, now())", product, user);
        assertRejected("insert into stock_movements (product_id, type, quantity, user_id, created_at) values (?, 'ENTRY', -5, ?, now())", product, user);
        assertRejected("insert into stock_movements (product_id, type, quantity, user_id, created_at) values (?, 'ENTRY', 0, ?, now())", product, user);

        assertAccepted("insert into stock_movements (product_id, type, quantity, sale_id, user_id, created_at) values (?, 'SALE', -1, ?, ?, now())", product, sale, user);
        assertRejected("insert into stock_movements (product_id, type, quantity, sale_id, user_id, created_at) values (?, 'SALE', 1, ?, ?, now())", product, sale, user);
        assertRejected("insert into stock_movements (product_id, type, quantity, user_id, created_at) values (?, 'SALE', -1, ?, now())", product, user);

        assertAccepted("insert into stock_movements (product_id, type, quantity, sale_id, user_id, created_at) values (?, 'RETURN', 1, ?, ?, now())", product, sale, user);
        assertRejected("insert into stock_movements (product_id, type, quantity, user_id, created_at) values (?, 'RETURN', 1, ?, now())", product, user);
    }

    @Test
    void adjustmentRequiresNotes() throws Exception {
        long user = insertUser("ana@example.com", "ana");
        long product = insertProduct(null);

        assertRejected("insert into stock_movements (product_id, type, quantity, user_id, created_at) values (?, 'ADJUSTMENT', -2, ?, now())", product, user);
        assertRejected("insert into stock_movements (product_id, type, quantity, user_id, created_at, notes) values (?, 'ADJUSTMENT', -2, ?, now(), '  ')", product, user);
        assertAccepted("insert into stock_movements (product_id, type, quantity, user_id, created_at, notes) values (?, 'ADJUSTMENT', -2, ?, now(), 'Quebra na prateleira')", product, user);
    }

    private long insertUser(String email, String username) throws SQLException {
        return insertReturningId("insert into users (name, email, username, password, role) values ('Usuario', ?, ?, 'x', 'ADMIN') returning id", email, username);
    }

    private long insertProduct(String barcode) throws SQLException {
        return insertReturningId("insert into products (name, barcode, price, unit, description, stock) values ('Produto', ?, 10, 'UN', 'd', 5) returning id", barcode);
    }

    private long insertSale(long userId) throws SQLException {
        return insertReturningId("insert into sales (date, subtotal, status, user_id) values (now(), 0, 'DRAFT', ?) returning id", userId);
    }

    private void assertAccepted(String sql, Object... params) {
        assertDoesNotThrow(() -> execute(sql, params));
    }

    private void assertRejected(String sql, Object... params) {
        SQLException e = assertThrows(SQLException.class, () -> execute(sql, params));
        assertTrue(e.getSQLState().startsWith("23"), () -> "expected an integrity violation, got: " + e.getMessage());
    }

    private static String query(String sql, Object... params) throws SQLException {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private static void execute(String sql, Object... params) throws SQLException {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private static long insertReturningId(String sql, Object... params) throws SQLException {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
