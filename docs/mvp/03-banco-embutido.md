# Passo 03: Ciclo de vida do PostgreSQL embutido

## Objetivo

Implementar a ADR-0004: a aplicação inicia o PostgreSQL embutido antes do Spring, garante uma única instância por diretório de dados, cria o banco `sells` e encerra tudo de forma limpa. O modo `external` continua disponível para desenvolvimento. Resolve D2.

Ao final deste passo, `./mvnw javafx:run` abre a janela provisória **sem Docker**.

## Pré-requisitos

Passo 02 concluído.

## Fora do escopo

- A senha do banco embutido (item 7 da ADR-0004) fica para depois do MVP. Não implemente.
- Não crie telas: a janela continua a provisória do passo 02.

## Configuração

A configuração é lida **antes** do Spring, de propriedades de sistema (`-Dchave=valor`) ou de variáveis de ambiente. A propriedade tem prioridade sobre a variável.

| Propriedade | Variável de ambiente | Padrão | Uso |
|---|---|---|---|
| `sells.db.mode` | `SELLS_DB_MODE` | `embedded` | `embedded` ou `external` |
| `sells.data.dir` | `SELLS_DATA_DIR` | `<home do usuário>/.sells` | Diretório de dados, lock, binários e logs |
| `sells.db.url` | `SELLS_DB_URL` | — | Obrigatória no modo `external` |
| `sells.db.username` | `SELLS_DB_USERNAME` | — | Modo `external` |
| `sells.db.password` | `SELLS_DB_PASSWORD` | — | Modo `external` |

Estrutura do diretório de dados (modo `embedded`):

```
~/.sells/
├── sells.lock     lock de instância única (FileChannel.tryLock)
├── pgdata/        diretório de dados do PostgreSQL
├── runtime/       binários do PostgreSQL extraídos pela biblioteca
└── logs/sells.log log da aplicação (também no modo external)
```

## Tarefas

Todas as classes de produção deste passo ficam em `src/main/java/com/github/svitorz/sells/infrastructure/database/` e **não usam Spring**.

### 3.1 `DatabaseSettings`

```java
package com.github.svitorz.sells.infrastructure.database;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;

/**
 * Database configuration resolved before Spring starts.
 */
public record DatabaseSettings(Mode mode, Path dataDir, String url, String username, String password) {

    public enum Mode {
        EMBEDDED,
        EXTERNAL
    }

    public static DatabaseSettings fromEnvironment() {
        return from(DatabaseSettings::lookupSystem);
    }

    static DatabaseSettings from(Function<String, String> lookup) {
        String modeValue = valueOrDefault(lookup.apply("sells.db.mode"), "embedded");
        Mode mode = switch (modeValue.trim().toLowerCase(Locale.ROOT)) {
            case "embedded" -> Mode.EMBEDDED;
            case "external" -> Mode.EXTERNAL;
            default -> throw new IllegalArgumentException(
                    "Invalid sells.db.mode '" + modeValue + "'. Use 'embedded' or 'external'.");
        };
        String dataDirValue = lookup.apply("sells.data.dir");
        Path dataDir = dataDirValue != null
                ? Path.of(dataDirValue)
                : Path.of(System.getProperty("user.home"), ".sells");
        String url = lookup.apply("sells.db.url");
        if (mode == Mode.EXTERNAL && url == null) {
            throw new IllegalArgumentException("sells.db.url is required when sells.db.mode=external");
        }
        return new DatabaseSettings(mode, dataDir.toAbsolutePath(), url,
                lookup.apply("sells.db.username"), lookup.apply("sells.db.password"));
    }

    private static String lookupSystem(String key) {
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String env = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        return env == null || env.isBlank() ? null : env;
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
```

### 3.2 Exceções

`InstanceAlreadyRunningException.java`:

```java
package com.github.svitorz.sells.infrastructure.database;

import java.nio.file.Path;

public class InstanceAlreadyRunningException extends RuntimeException {

    public InstanceAlreadyRunningException(Path lockFile) {
        super("Another Sells instance is already using " + lockFile.getParent());
    }
}
```

`DatabaseStartupException.java`:

```java
package com.github.svitorz.sells.infrastructure.database;

public class DatabaseStartupException extends RuntimeException {

    public DatabaseStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 3.3 `InstanceLock`

```java
package com.github.svitorz.sells.infrastructure.database;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Cross-process lock that keeps a single application per data directory
 * (ADR-0004, item 6).
 */
final class InstanceLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    private InstanceLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    static InstanceLock acquire(Path lockFile) throws IOException {
        Files.createDirectories(lockFile.getParent());
        FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new InstanceAlreadyRunningException(lockFile);
            }
            return new InstanceLock(channel, lock);
        } catch (OverlappingFileLockException e) {
            channel.close();
            throw new InstanceAlreadyRunningException(lockFile);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }
}
```

### 3.4 `DatabaseBootstrap`

```java
package com.github.svitorz.sells.infrastructure.database;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Starts the database before Spring (ADR-0004) and stops it on shutdown.
 * In EMBEDDED mode it owns a PostgreSQL process; in EXTERNAL mode it only
 * forwards the configured connection settings.
 */
public final class DatabaseBootstrap implements AutoCloseable {

    static final String DATABASE_NAME = "sells";
    static final String EMBEDDED_USER = "postgres";

    private static final Logger log = LoggerFactory.getLogger(DatabaseBootstrap.class);

    private final DatabaseSettings settings;
    private final InstanceLock lock;
    private final EmbeddedPostgres postgres;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private DatabaseBootstrap(DatabaseSettings settings, InstanceLock lock, EmbeddedPostgres postgres,
                              String jdbcUrl, String username, String password) {
        this.settings = settings;
        this.lock = lock;
        this.postgres = postgres;
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public static DatabaseBootstrap start(DatabaseSettings settings) {
        if (settings.mode() == DatabaseSettings.Mode.EXTERNAL) {
            log.info("Using external database {}", settings.url());
            return new DatabaseBootstrap(settings, null, null,
                    settings.url(), settings.username(), settings.password());
        }
        return startEmbedded(settings);
    }

    private static DatabaseBootstrap startEmbedded(DatabaseSettings settings) {
        InstanceLock lock = null;
        EmbeddedPostgres postgres = null;
        try {
            lock = InstanceLock.acquire(settings.dataDir().resolve("sells.lock"));
            Path runtimeDir = Files.createDirectories(settings.dataDir().resolve("runtime"));
            log.info("Starting embedded PostgreSQL in {}", settings.dataDir());
            postgres = EmbeddedPostgres.builder()
                    .setDataDirectory(settings.dataDir().resolve("pgdata"))
                    .setCleanDataDirectory(false)
                    .setOverrideWorkingDirectory(runtimeDir.toFile())
                    .setRegisterShutdownHook(false)
                    .setPGStartupWait(Duration.ofSeconds(30))
                    .setServerConfig("listen_addresses", "localhost")
                    // The library is meant for tests: it starts postgres with -F (fsync off)
                    // and synchronous_commit=off. A sales system must survive power loss,
                    // so both are turned back on. The -c options come after -F and win.
                    .setServerConfig("fsync", "on")
                    .setServerConfig("synchronous_commit", "on")
                    .setServerConfig("full_page_writes", "on")
                    .start();
            ensureDatabaseExists(postgres);
            DatabaseBootstrap bootstrap = new DatabaseBootstrap(settings, lock, postgres,
                    postgres.getJdbcUrl(EMBEDDED_USER, DATABASE_NAME), EMBEDDED_USER, "");
            Runtime.getRuntime().addShutdownHook(new Thread(bootstrap::close, "sells-database-shutdown"));
            log.info("Embedded PostgreSQL started on port {}", postgres.getPort());
            return bootstrap;
        } catch (InstanceAlreadyRunningException e) {
            throw e;
        } catch (IOException | SQLException | RuntimeException e) {
            closeQuietly(postgres);
            closeQuietly(lock);
            throw new DatabaseStartupException("Could not start the embedded PostgreSQL", e);
        }
    }

    private static void ensureDatabaseExists(EmbeddedPostgres postgres) throws SQLException {
        try (Connection connection = postgres.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement()) {
            boolean exists;
            try (ResultSet rs = statement.executeQuery(
                    "select 1 from pg_database where datname = '" + DATABASE_NAME + "'")) {
                exists = rs.next();
            }
            if (!exists) {
                statement.execute("create database " + DATABASE_NAME);
                log.info("Created database {}", DATABASE_NAME);
            }
        }
    }

    /**
     * Properties handed to SpringApplicationBuilder#properties. They have the
     * lowest precedence, so application.properties must not define them.
     */
    public Map<String, Object> springProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", jdbcUrl);
        properties.put("spring.datasource.username", username == null ? "" : username);
        properties.put("spring.datasource.password", password == null ? "" : password);
        properties.put("logging.file.name", settings.dataDir().resolve("logs").resolve("sells.log").toString());
        return properties;
    }

    public DatabaseSettings settings() {
        return settings;
    }

    /** Stops PostgreSQL (pg_ctl stop -m fast) and releases the lock. Safe to call more than once. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeQuietly(postgres);
        closeQuietly(lock);
        if (postgres != null) {
            log.info("Embedded PostgreSQL stopped");
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            log.warn("Error while closing {}", closeable.getClass().getSimpleName(), e);
        }
    }
}
```

> `EmbeddedPostgres` implementa `java.io.Closeable`, então é aceito por `closeQuietly(AutoCloseable)`.

### 3.5 Usar o bootstrap em `SellsFxApplication`

Substitua o conteúdo de `SellsFxApplication` por este. A mensagem de erro de inicialização é exibida em português, porque o usuário final a vê.

```java
package com.github.svitorz.sells;

import com.github.svitorz.sells.infrastructure.database.DatabaseBootstrap;
import com.github.svitorz.sells.infrastructure.database.DatabaseSettings;
import com.github.svitorz.sells.infrastructure.database.InstanceAlreadyRunningException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX lifecycle: database, then Spring, then the window. Shutdown runs in reverse order.
 */
public class SellsFxApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(SellsFxApplication.class);

    private DatabaseBootstrap database;
    private ConfigurableApplicationContext context;
    private String startupError;

    @Override
    public void init() {
        try {
            database = DatabaseBootstrap.start(DatabaseSettings.fromEnvironment());
            context = new SpringApplicationBuilder(SellsApplication.class)
                    .headless(false)
                    .web(WebApplicationType.NONE)
                    .properties(database.springProperties())
                    .run(getParameters().getRaw().toArray(String[]::new));
        } catch (InstanceAlreadyRunningException e) {
            log.warn(e.getMessage());
            startupError = "O Sells já está aberto neste computador.";
            shutdown();
        } catch (RuntimeException e) {
            log.error("Startup failed", e);
            startupError = "Não foi possível iniciar o Sells.\n\n" + e.getMessage();
            shutdown();
        }
    }

    @Override
    public void start(Stage stage) {
        if (startupError != null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, startupError);
            alert.setHeaderText("Erro ao iniciar");
            alert.showAndWait();
            Platform.exit();
            return;
        }
        stage.setTitle("Sells");
        stage.setScene(new Scene(new StackPane(new Label("Sells")), 800, 500));
        stage.setOnCloseRequest(event -> Platform.exit());
        stage.show();
    }

    @Override
    public void stop() {
        shutdown();
    }

    private void shutdown() {
        if (context != null) {
            context.close();
            context = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
    }
}
```

## Testes obrigatórios

Crie em `src/test/java/com/github/svitorz/sells/infrastructure/database/`. Use `@TempDir Path dir` do JUnit 5 como diretório de dados em todos os testes. Sempre feche o bootstrap com try-with-resources ou `finally`.

`DatabaseSettingsTest` (usa `DatabaseSettings.from(Map.of(...)::get)`):

| Teste | Verifica |
|---|---|
| `defaultsToEmbeddedModeInUserHome` | Com o mapa vazio, `mode == EMBEDDED` e `dataDir` termina em `.sells` |
| `readsExternalSettings` | Com `sells.db.mode=external`, url, username e password, os valores são retornados |
| `externalModeRequiresUrl` | `external` sem url lança `IllegalArgumentException` |
| `rejectsUnknownMode` | `sells.db.mode=sqlite` lança `IllegalArgumentException` |
| `usesConfiguredDataDir` | `sells.data.dir=<dir>` resulta em `dataDir` igual a `dir.toAbsolutePath()` |

`InstanceLockTest`:

| Teste | Verifica |
|---|---|
| `secondAcquireFailsWhileFirstIsHeld` | Dois `acquire` no mesmo arquivo: o segundo lança `InstanceAlreadyRunningException` |
| `canAcquireAgainAfterClose` | Depois de `close()`, um novo `acquire` funciona |

`DatabaseBootstrapTest` (modo embedded, `new DatabaseSettings(Mode.EMBEDDED, dir, null, null, null)`). Para consultar, use `DriverManager.getConnection(url, "postgres", "")` com a url de `springProperties()`.

| Teste | Verifica |
|---|---|
| `startsWithEmptyDirectoryAndCreatesDatabase` | `select current_database()` retorna `sells`, e `dir/pgdata` e `dir/sells.lock` existem |
| `keepsDataAcrossRestarts` | Cria uma tabela e insere uma linha, fecha, inicia de novo no mesmo diretório e a linha continua lá |
| `rejectsSecondInstanceOnSameDataDirectory` | Com um bootstrap aberto, um segundo `start` no mesmo diretório lança `InstanceAlreadyRunningException` |
| `closeIsIdempotent` | Chamar `close()` duas vezes não lança exceção |
| `runsWithDurableSettings` | `show fsync` retorna `on` e `show synchronous_commit` retorna `on`. **Se este teste falhar, pare e reporte**: isso significa que a sobrescrita das configurações da biblioteca não funcionou, e o banco pode perder vendas numa queda de energia. |
| `externalModeOnlyForwardsSettings` | Em modo `EXTERNAL` com url `jdbc:postgresql://example:5432/x`, `springProperties()` contém essa url, e `dir/pgdata` não é criado |

> O PostgreSQL se recusa a rodar como `root`. Se os testes falharem com "cannot be run as root", rode o build com um usuário comum e registre isso no relatório.

## Documentação a atualizar

1. `docs/adr/0004-ciclo-de-vida-do-banco-embutido.md`:
   - Status: `Aceita (implementada, exceto o item 7)`.
   - No item 1, depois de "`~/.sells/pgdata`", acrescente: "Os binários extraídos ficam em `~/.sells/runtime` e o log em `~/.sells/logs/sells.log`. O diretório pode ser alterado por `sells.data.dir` ou `SELLS_DATA_DIR`."
   - No item 7, acrescente ao final: "Enquanto isso não é implementado, o servidor aceita conexões apenas em `localhost` (`listen_addresses`)."
   - Acrescente o item 9: "**Durabilidade:** a biblioteca inicia o PostgreSQL com `fsync` e `synchronous_commit` desligados, o que é adequado para testes, mas não para dados de vendas. A aplicação religa `fsync`, `synchronous_commit` e `full_page_writes`. O teste `runsWithDurableSettings` garante isso."
   - Na seção "Consequências", acrescente: "A implementação está em `infrastructure.database.DatabaseBootstrap`. Os testes de início com dados limpos, reinício e bloqueio de segunda instância estão em `DatabaseBootstrapTest`. A recuperação após queda foi validada no spike da ADR-0003 e não tem teste automatizado."
2. `docs/adr/0003-distribuicao-com-postgresql-embutido.md`, em "Achados que afetam a implementação", acrescente: "A biblioteca inicia o PostgreSQL com `fsync` e `synchronous_commit` desligados. As medições acima foram feitas nessas condições. A aplicação religa os dois (ADR-0004, item 9), o que pode deixar as escritas um pouco mais lentas."
3. `README.md`: no bloco "Desenvolvimento", acrescente:

   ```
   Abrir a aplicação (o banco é criado em ~/.sells na primeira execução):

       ./mvnw javafx:run
   ```

## Critérios de aceite

1. `./mvnw -B verify` termina com `BUILD SUCCESS`.
2. `./mvnw javafx:run` abre a janela "Sells" sem Docker. Ao fechar, o processo `postgres` termina (verifique com `pgrep -fa postgres` no Linux ou no Gerenciador de Tarefas no Windows).
3. Com a janela aberta, um segundo `./mvnw javafx:run` mostra o alerta "O Sells já está aberto neste computador." e termina.
4. Os critérios 2 e 3 são manuais. Se o ambiente de execução não tiver display gráfico, registre no relatório que eles ficaram para a pessoa responsável validar.

## Commit

```
feat(db): manage embedded PostgreSQL lifecycle with single-instance lock
```
