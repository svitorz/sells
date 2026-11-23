package com.github.svitorz.sells;

import com.github.svitorz.sells.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConnectionTest extends IntegrationTest {

  @Autowired
  private DataSource dataSource;

  @Test
  void shouldConnectToDatabase() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      assertTrue(connection.isValid(2));
    }
  }
}
