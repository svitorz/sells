# Passo 01: Saneamento do build, da configuração e dos testes

## Objetivo

Deixar `./mvnw -B verify` verde **sem Docker e sem PostgreSQL instalado**, com dependências coerentes e sem configuração morta. Resolve D3, D4, D5, D6 e D7 e parte de D1 e D2 (ver `README.md` deste diretório).

## Pré-requisitos

- Branch atual com o commit `009c4ba` ou posterior.
- Acesso à internet para o Maven baixar dependências.

## Fora do escopo

- Não crie pacotes novos de arquitetura (isso é o passo 02).
- Não implemente o banco embutido da aplicação (isso é o passo 03).
- Não mexa nas regras das entidades além do que está descrito aqui.

> Depois deste passo e até o fim do passo 03, `./mvnw javafx:run` **não** vai conseguir conectar ao banco, porque a URL fixa foi removida. Isso é esperado.

## Tarefas

### 1.1 `pom.xml`

Faça exatamente estas alterações:

1. Em `<name>`, `<description>` e `<url>`, use:
   ```xml
   <name>Sells</name>
   <description>Sistema de vendas desktop, livre e gratuito, para pequenos comércios</description>
   <url>https://github.com/svitorz/sells</url>
   ```
2. Troque o bloco `<licenses>` vazio por:
   ```xml
   <licenses>
       <license>
           <name>GPL-3.0-or-later</name>
           <url>https://www.gnu.org/licenses/gpl-3.0.html</url>
       </license>
   </licenses>
   ```
3. **Mantenha** o bloco `<developers><developer/></developers>` como está. Ele existe para anular a herança do `spring-boot-starter-parent`. Substitua o bloco `<scm>` inteiro por:
   ```xml
   <scm>
       <url>https://github.com/svitorz/sells</url>
   </scm>
   ```
4. Na dependência `org.flywaydb:flyway-database-postgresql`, **remova a linha `<version>10.11.0</version>`**, para que a versão seja a gerenciada pelo Spring Boot.
5. **Remova** estas dependências:
   - `org.springframework.boot:spring-boot-starter-validation-test`
   - `jakarta.validation:jakarta.validation-api`
   - `me.paulschwarz:spring-dotenv`
6. **Adicione** estas dependências:
   ```xml
   <dependency>
       <groupId>org.springframework.security</groupId>
       <artifactId>spring-security-crypto</artifactId>
   </dependency>

   <dependency>
       <groupId>com.tngtech.archunit</groupId>
       <artifactId>archunit-junit5</artifactId>
       <version>1.4.1</version>
       <scope>test</scope>
   </dependency>
   ```
   (`spring-security-crypto` não leva versão: ela é gerenciada pelo Spring Boot.)
7. Na seção `<build><plugins>`, **remova** o plugin `spring-boot-maven-plugin`. A distribuição não usa fat jar (ver passo 13).
8. Normalize a indentação do arquivo inteiro para 4 espaços (hoje há uma mistura de tabs e espaços). Não mude nenhum conteúdo além do que foi pedido acima.

### 1.2 Remover as anotações `jakarta.validation`

Como a API de validação saiu do classpath, remova de **todas** as classes em `src/main/java/com/github/svitorz/sells/domain/` os imports `jakarta.validation.constraints.*` e as anotações `@NotBlank`, `@NotNull`, `@Email`, `@Pattern` e `@DecimalMin`. Não altere mais nada nessas classes. As regras serão reimplementadas no domínio puro, no passo 04.

### 1.3 `src/main/resources/application.properties`

Substitua o conteúdo inteiro por:

```properties
spring.application.name=sells
spring.main.web-application-type=none
spring.main.banner-mode=off

# The datasource URL, username and password are provided at startup by
# DatabaseBootstrap (step 03) or by the test support classes.
spring.datasource.hikari.maximum-pool-size=5

spring.flyway.locations=classpath:db/migration
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
```

### 1.4 Suporte de banco para testes

Crie `src/test/java/com/github/svitorz/sells/support/TestDatabase.java`:

```java
package com.github.svitorz.sells.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Starts a single embedded PostgreSQL for the whole test JVM and creates the
 * "sells" database on it. Flyway migrations are applied by Spring Boot.
 */
public final class TestDatabase {

    public static final String DATABASE = "sells";
    public static final String USERNAME = "postgres";
    public static final String PASSWORD = "";

    private static EmbeddedPostgres postgres;

    private TestDatabase() {
    }

    public static synchronized String jdbcUrl() {
        start();
        return postgres.getJdbcUrl(USERNAME, DATABASE);
    }

    private static void start() {
        if (postgres != null) {
            return;
        }
        try {
            postgres = EmbeddedPostgres.builder()
                    .setDataDirectory(Path.of("target", "test-pgdata"))
                    .setCleanDataDirectory(true)
                    .setRegisterShutdownHook(true)
                    .start();
            try (Connection connection = postgres.getPostgresDatabase().getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("select 1 from pg_database where datname = '" + DATABASE + "'")) {
                if (!rs.next()) {
                    statement.execute("create database " + DATABASE);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not start the embedded PostgreSQL used by tests", e);
        }
    }
}
```

Crie `src/test/java/com/github/svitorz/sells/support/IntegrationTest.java`:

```java
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
```

> A regra 6.2.4 do README (injeção só por construtor) vale para o código de produção. Em classes de teste do JUnit, `@Autowired` em campo é permitido.

### 1.5 Corrigir os dois testes que dependem de banco externo

- `SellsApplicationTests`: passe a estender `IntegrationTest` e remova a anotação `@SpringBootTest` da classe (ela é herdada).
- `DatabaseConnectionTest`: passe a estender `IntegrationTest`, remova `@SpringBootTest` e mantenha o teste `shouldConnectToDatabase`.
- Não altere o `SchemaConstraintsTest`.

### 1.6 Docker e ambiente de desenvolvimento

1. **Apague** `Dockerfile` e `.dockerignore`. Motivo: o app é desktop e a distribuição é nativa (ADR-0003, ADR-0012).
2. Substitua o `compose.yml` inteiro por:

```yaml
# Development database for the "external" mode (see CONTRIBUTING.md).
# End users never need Docker: the distributed application embeds PostgreSQL.
services:
  sells-postgres:
    container_name: sells-postgres
    image: postgres:${DB_VERSION:-16}-alpine
    environment:
      POSTGRES_DB: ${DB_NAME:-sells}
      POSTGRES_USER: ${DB_USER:-postgres}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-postgres}
    restart: unless-stopped
    shm_size: 128mb
    ports:
      - "${DB_FORWARD_PORT:-5432}:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - sells-network

  pg-admin-sells:
    image: dpage/pgadmin4:latest
    restart: unless-stopped
    container_name: sells-pgadmin
    profiles: ["tools"]
    environment:
      PGADMIN_DEFAULT_EMAIL: ${PGADMIN_DEFAULT_EMAIL:-admin@example.com}
      PGADMIN_DEFAULT_PASSWORD: ${PGADMIN_DEFAULT_PASSWORD:-admin}
      PGPASS_FILE: /config/pgpass
    ports:
      - "${PGADMIN_FORWARD_PORT:-8080}:80"
    volumes:
      - pgadmin_data:/var/lib/pgadmin
    depends_on:
      sells-postgres:
        condition: service_healthy
    networks:
      - sells-network
    configs:
      - source: pgadmin_servers
        target: /pgadmin4/servers.json
        mode: 0444
      - source: pgadmin_pass
        target: /config/pgpass
        uid: "5050"
        gid: "5050"
        mode: 0600

volumes:
  postgres_data:
  pgadmin_data:

networks:
  sells-network:
    driver: bridge

configs:
  pgadmin_servers:
    content: |
      {
        "Servers": {
          "1": {
            "Name": "Sells (desenvolvimento)",
            "Group": "Servers",
            "Host": "sells-postgres",
            "Port": 5432,
            "MaintenanceDB": "${DB_NAME:-sells}",
            "Username": "${DB_USER:-postgres}",
            "PassFile": "/config/pgpass",
            "SSLMode": "prefer"
          }
        }
      }
  pgadmin_pass:
    content: |
      sells-postgres:5432:${DB_NAME:-sells}:${DB_USER:-postgres}:${DB_PASSWORD:-postgres}
```

3. Substitua o `.env.example` inteiro por:

```properties
# Used only by compose.yml (development database). The application itself
# reads SELLS_* variables, documented in CONTRIBUTING.md.
DB_VERSION=16
DB_NAME=sells
DB_USER=postgres
DB_PASSWORD=postgres
DB_FORWARD_PORT=5432

PGADMIN_DEFAULT_EMAIL=admin@example.com
PGADMIN_DEFAULT_PASSWORD=admin
PGADMIN_FORWARD_PORT=8080
```

4. **Não** altere o arquivo `.env` local (ele não é versionado e pertence a quem desenvolve).

### 1.7 README (correção mínima)

No `README.md`, substitua as seções de configuração e execução por este texto provisório. O README definitivo é escrito no passo 13.

```markdown
# Sells

Sistema de vendas desktop, livre e gratuito (GPLv3), para pequenos comércios.

> Projeto em desenvolvimento. O plano até a primeira versão está em `docs/mvp/`.

## Desenvolvimento

Requisitos: JDK 21 ou mais novo. Docker não é necessário.

Rodar os testes (o PostgreSQL de teste é embutido e iniciado automaticamente):

    ./mvnw -B verify
```

## Testes obrigatórios

Nenhum teste novo neste passo. Todos os 14 testes existentes devem passar sem Docker.

## Documentação a atualizar

- `README.md`: conforme 1.7.
- Não há mudança nas ADRs neste passo.

## Critérios de aceite

1. Com o Docker parado e sem PostgreSQL local, `./mvnw -B verify` termina com `BUILD SUCCESS` e `Tests run: 14, Failures: 0, Errors: 0`.
2. `grep -rn "jakarta.validation" src/` não retorna nada.
3. `grep -n "10.11.0\|spring-dotenv\|spring.batch" pom.xml src/main/resources/application.properties` não retorna nada.
4. Os arquivos `Dockerfile` e `.dockerignore` não existem mais.
5. `docker compose config` (se o Docker estiver instalado) valida o `compose.yml` sem erros. Se o Docker não estiver instalado, registre isso no relatório.

## Commit

```
build: fix dependency versions and run tests on embedded PostgreSQL
```
