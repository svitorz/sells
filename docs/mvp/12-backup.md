# Passo 12: Exportar e restaurar backup (UC19)

## Objetivo

Permitir que o administrador gere um arquivo de backup com o sistema em uso e restaure esse arquivo depois, inclusive em outro computador. Resolve D11.

## Decisão técnica

O pacote de binários do PostgreSQL embutido não inclui `pg_dump` nem `pg_restore` (D11). Por isso, o backup é **lógico**, feito via JDBC com `COPY ... TO STDOUT` e `COPY ... FROM STDIN` em formato CSV, dentro de um arquivo `.zip`. Isso funciona com o banco ligado, também no modo `external`, e não depende da versão maior do PostgreSQL. A decisão é registrada na ADR-0014 (tarefa 12.6).

Formato do arquivo (versão 1):

```
sells-backup-20260115-1830.zip
├── manifest.properties   format=1, schemaVersion=<versão Flyway>, createdAt=<ISO-8601>
├── users.csv
├── categories.csv
├── products.csv
├── product_category.csv
├── customers.csv
├── sales.csv
├── sale_items.csv
└── stock_movements.csv
```

As entradas estão sempre **nessa ordem**, que respeita as chaves estrangeiras. `manifest.properties` é sempre a primeira entrada.

## Pré-requisitos

Passo 11 concluído.

## Tarefas

### 12.1 Aplicação (`application.backup`)

`BackupException.java`: `extends RuntimeException`, com os construtores `(String message)` e `(String message, Throwable cause)`. A mensagem é exibida ao usuário.

Em `application.port`, acrescente:

```java
public interface BackupGateway {
    /** Writes a complete, consistent backup to out. Does not close out. */
    void exportTo(OutputStream out);

    /** Replaces all data with the backup read from in, in a single transaction. Does not close in. */
    void restoreFrom(InputStream in);
}
```

`BackupService.java`:

```java
package com.github.svitorz.sells.application.backup;

import com.github.svitorz.sells.application.port.BackupGateway;
import com.github.svitorz.sells.application.session.SessionContext;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class BackupService {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final BackupGateway gateway;
    private final SessionContext session;
    private final Clock clock;

    public BackupService(BackupGateway gateway, SessionContext session, Clock clock) {
        this.gateway = gateway;
        this.session = session;
        this.clock = clock;
    }

    public String suggestedFileName() {
        return "sells-backup-" + FILE_STAMP.format(LocalDateTime.now(clock)) + ".zip";
    }

    /** Writes to a temporary file first, so a failed export never leaves a broken file behind. */
    public void exportTo(Path file) {
        session.requireAdmin();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temporary))) {
                gateway.exportTo(out);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BackupException("Não foi possível gravar o arquivo de backup.", e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    /** Replaces every record. The session ends, because the logged user may no longer exist. */
    public void restoreFrom(Path file) {
        session.requireAdmin();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            gateway.restoreFrom(in);
        } catch (IOException e) {
            throw new BackupException("Não foi possível ler o arquivo de backup.", e);
        }
        session.end();
    }
}
```

Em `ApplicationServicesConfig`, acrescente o `@Bean BackupService backupService(BackupGateway gateway, SessionContext session, Clock clock)`.

### 12.2 Adaptador (`infrastructure.backup.PostgresCopyBackupGateway`)

```java
package com.github.svitorz.sells.infrastructure.backup;

import com.github.svitorz.sells.application.backup.BackupException;
import com.github.svitorz.sells.application.port.BackupGateway;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
public class PostgresCopyBackupGateway implements BackupGateway {

    static final String FORMAT = "1";
    static final String MANIFEST = "manifest.properties";
    /** Foreign-key order: parents first. */
    static final List<String> TABLES = List.of("users", "categories", "products", "product_category",
            "customers", "sales", "sale_items", "stock_movements");
    static final List<String> IDENTITY_TABLES = List.of("users", "categories", "products",
            "customers", "sales", "sale_items", "stock_movements");

    private final DataSource dataSource;
    private final Clock clock;

    public PostgresCopyBackupGateway(DataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    @Override
    public void exportTo(OutputStream out) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true);
            try {
                CopyManager copy = connection.unwrap(PGConnection.class).getCopyAPI();
                ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
                zip.putNextEntry(new ZipEntry(MANIFEST));
                Properties manifest = new Properties();
                manifest.setProperty("format", FORMAT);
                manifest.setProperty("schemaVersion", schemaVersion(connection));
                manifest.setProperty("createdAt", LocalDateTime.now(clock).toString());
                manifest.store(zip, "Sells backup");
                zip.closeEntry();
                for (String table : TABLES) {
                    zip.putNextEntry(new ZipEntry(table + ".csv"));
                    copy.copyOut("COPY " + table + " TO STDOUT WITH (FORMAT csv, HEADER true)", zip);
                    zip.closeEntry();
                }
                zip.finish();
                connection.commit();
            } catch (SQLException | IOException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setReadOnly(false);
            }
        } catch (SQLException | IOException e) {
            throw new BackupException("Não foi possível gerar o backup.", e);
        }
    }

    @Override
    public void restoreFrom(InputStream in) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                CopyManager copy = connection.unwrap(PGConnection.class).getCopyAPI();
                ZipInputStream zip = new ZipInputStream(in, StandardCharsets.UTF_8);
                InputStream entryStream = new FilterInputStream(zip) {
                    @Override
                    public void close() {
                        // the zip stream is closed by the caller
                    }
                };
                ZipEntry entry = zip.getNextEntry();
                if (entry == null || !MANIFEST.equals(entry.getName())) {
                    throw new BackupException("O arquivo escolhido não é um backup do Sells.");
                }
                Properties manifest = new Properties();
                manifest.load(entryStream);
                validate(manifest, schemaVersion(connection));
                try (Statement statement = connection.createStatement()) {
                    statement.execute("TRUNCATE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE");
                }
                for (String table : TABLES) {
                    entry = zip.getNextEntry();
                    if (entry == null || !(table + ".csv").equals(entry.getName())) {
                        throw new BackupException("O arquivo de backup está incompleto ou corrompido.");
                    }
                    copy.copyIn("COPY " + table + " FROM STDIN WITH (FORMAT csv, HEADER true)", entryStream);
                }
                resetIdentities(connection);
                connection.commit();
            } catch (SQLException | IOException | RuntimeException e) {
                connection.rollback();
                if (e instanceof BackupException backupException) {
                    throw backupException;
                }
                throw new BackupException("Não foi possível restaurar o backup. Nenhum dado foi alterado.", e);
            }
        } catch (SQLException e) {
            throw new BackupException("Não foi possível restaurar o backup. Nenhum dado foi alterado.", e);
        }
    }

    private static void validate(Properties manifest, String currentVersion) {
        if (!FORMAT.equals(manifest.getProperty("format"))) {
            throw new BackupException("Formato de backup não suportado.");
        }
        String backupVersion = manifest.getProperty("schemaVersion");
        if (!currentVersion.equals(backupVersion)) {
            throw new BackupException("Este backup foi gerado por outra versão do Sells (banco versão "
                    + backupVersion + "). Versão atual do banco: " + currentVersion + ".");
        }
    }

    private static String schemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select version from flyway_schema_history "
                     + "where success and version is not null order by installed_rank desc limit 1")) {
            if (!rs.next()) {
                throw new BackupException("O banco de dados não foi inicializado.");
            }
            return rs.getString(1);
        }
    }

    private static void resetIdentities(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String table : IDENTITY_TABLES) {
                statement.execute("select setval(pg_get_serial_sequence('" + table + "', 'id'), "
                        + "coalesce((select max(id) from " + table + "), 0) + 1, false)");
            }
        }
    }
}
```

> **Ponto de atenção:** as colunas `id` são `GENERATED ALWAYS AS IDENTITY`. O `COPY FROM` do PostgreSQL grava os ids fornecidos mesmo assim, porque essa restrição só vale para `INSERT`. Isso foi verificado em 2026-11-23 com o PostgreSQL embutido 16.15: o ciclo `COPY TO`, `TRUNCATE ... RESTART IDENTITY`, `COPY FROM` e `setval(pg_get_serial_sequence(...))` preservou os ids, e o insert seguinte recebeu `max(id) + 1`. Se mesmo assim o teste de ida e volta falhar com o erro `cannot insert a non-DEFAULT value into column "id"`, **pare e reporte**. Não troque o tipo das colunas por conta própria.

Os nomes de tabela vêm de constantes, nunca da entrada do usuário. Por isso, a concatenação de SQL acima é segura.

### 12.3 Mensagens de erro

Em `ui.support.ErrorMessages`, acrescente `BackupException` à lista de exceções cuja mensagem é exibida diretamente.

### 12.4 Tela (`ui.backup.BackupView`)

Construtor: `BackupView(BackupService backupService, Runnable onRestored)`.

Conteúdo (classe `page`):

1. O título `Backup`.
2. O texto (com quebra automática): `O backup copia todos os dados do sistema (vendas, produtos, clientes, usuários e estoque) para um único arquivo. Faça backups com frequência e guarde cópias fora deste computador, por exemplo em um pendrive ou na nuvem.`
3. O botão `Exportar backup…`:
   - `FileChooser` de salvar, com o título `Salvar backup`, o nome inicial `backupService.suggestedFileName()` e o filtro `Backup do Sells (*.zip)`.
   - Se o arquivo escolhido não terminar em `.zip`, acrescente a extensão.
   - Execute com `FxAsync.run(() -> { backupService.exportTo(path); return path; }, ...)`. Durante a execução, os dois botões ficam desabilitados e um `ProgressIndicator` fica visível.
   - Em caso de sucesso: `Dialogs.info(..., "Backup salvo em " + path + ".")`. Em caso de erro: `Dialogs.error(..., ErrorMessages.of(erro))`.
4. O botão `Restaurar backup…`:
   - `FileChooser` de abrir, com o mesmo filtro.
   - `Dialogs.textInput(owner, "Restaurar backup", "Todos os dados atuais serão SUBSTITUÍDOS pelos dados de \"" + nome + "\". Esta ação não pode ser desfeita. Recomendamos exportar um backup antes.\n\nPara confirmar, digite RESTAURAR:", false)`.
   - Continue apenas se o texto, com `trim()`, for exatamente `RESTAURAR`. Caso contrário, mostre `Dialogs.info(..., "Restauração cancelada.")`.
   - Execute com `FxAsync`, como na exportação. Em caso de sucesso: `Dialogs.info(..., "Backup restaurado. Entre novamente no sistema.")` e depois `onRestored.run()`.

Na `ViewFactory`, receba também o `BackupService` no construtor e acrescente `public Parent backup(Runnable onRestored)`. No `ShellView`, o item `Backup` chama `views.backup(onLogout)`. Assim, depois da restauração, o usuário volta ao login, ou ao cadastro do primeiro administrador se o backup não tiver usuários ativos.

### 12.5 Testes obrigatórios

`src/test/java/com/github/svitorz/sells/application/backup/BackupServiceTest.java` (unitário, com um `BackupGateway` falso escrito no próprio teste):

1. `suggestedFileName()` com o relógio em `2026-01-15T18:30` (UTC) retorna `sells-backup-20260115-1830.zip`.
2. `exportTo` e `restoreFrom` sem sessão de admin lançam `AccessDeniedException`.
3. Se o gateway lançar exceção durante `exportTo`, nem o arquivo final nem o `.tmp` existem depois (`@TempDir`).
4. `restoreFrom` bem-sucedido encerra a sessão (`session.current()` vazio).

`src/test/java/com/github/svitorz/sells/infrastructure/backup/BackupRoundTripTest.java` (estende `ServiceIntegrationTest`):

1. **Ida e volta:**
   - Monte um cenário com admin, 1 operador, 2 categorias, 3 produtos (um deles excluído), 2 clientes, 1 venda concluída, 1 venda cancelada e 1 rascunho. Registre a contagem de linhas de cada uma das 8 tabelas com `jdbcTemplate`.
   - Exporte para um arquivo em `@TempDir`.
   - Altere os dados: crie um produto, exclua um cliente e finalize o rascunho.
   - Restaure.
   - As contagens são iguais às registradas, o estoque de cada produto é igual ao de antes da exportação e o status de cada venda também.
   - Depois do login (`userService.login("admin", "secret123")` funciona, porque o hash foi restaurado), um produto novo recebe `id = max(id) + 1` (a sequência foi ajustada).
2. **Arquivo inválido:** um arquivo de texto qualquer lança `BackupException` com `O arquivo escolhido não é um backup do Sells.`, e os dados não mudam.
3. **Versão diferente:** um zip montado no teste, com `manifest.properties` contendo `format=1` e `schemaVersion=999`, lança a mensagem de versão diferente, e os dados não mudam.
4. **Backup incompleto:** um zip com o manifesto correto e apenas `users.csv` lança `O arquivo de backup está incompleto ou corrompido.`, e **os dados não mudam**. Isso prova que o `TRUNCATE` foi desfeito.

### 12.6 Documentação a atualizar

1. Crie `docs/adr/0014-backup-logico-com-copy.md`:

```markdown
# ADR-0014: Backup lógico com COPY

- **Status:** Aceita
- **Data:** 2026-11-23

## Contexto

O UC19 exige exportar e restaurar os dados. A ADR-0003 previa usar `pg_dump` e `pg_restore`, mas o pacote de binários do PostgreSQL embutido (`io.zonky.test.postgres`) contém apenas `initdb`, `pg_ctl` e `postgres`.

## Decisão

1. O backup é um arquivo `.zip` com um `manifest.properties` e um CSV por tabela, gerado com `COPY ... TO STDOUT` via JDBC, numa transação `REPEATABLE READ` somente leitura, o que garante um retrato consistente com o sistema em uso.
2. A restauração acontece numa única transação: `TRUNCATE` de todas as tabelas, `COPY ... FROM STDIN` em ordem de chaves estrangeiras e ajuste das sequências de identidade. Qualquer erro desfaz tudo.
3. O manifesto guarda a versão do schema (Flyway). Um backup só é restaurado no mesmo schema em que foi gerado.

## Motivo

- Não depende de binários extras nem da versão maior do PostgreSQL. O mesmo arquivo serve para migrar o sistema para um PostgreSQL mais novo.
- Funciona igual nos modos `embedded` e `external`.
- CSV é um formato aberto: o usuário pode abrir os próprios dados em uma planilha.

## Consequências

- Restaurar um backup de outra versão do schema não é possível no MVP. Quando houver uma V2, será preciso aplicar as migrations depois da restauração, o que fica registrado como trabalho futuro.
- Toda tabela nova precisa ser incluída na lista de tabelas do backup, na ordem correta. O teste `BackupRoundTripTest` falha se a contagem de linhas divergir.
```

2. `docs/use-cases.md`, UC19, troque a última regra ("Os binários `pg_dump`...") por: "O backup é lógico (ADR-0014). A restauração só aceita backups gerados na mesma versão do banco e é feita em uma única transação: em caso de erro, nenhum dado é alterado. Depois da restauração, o usuário precisa entrar novamente." Na regra de confirmação, acrescente: "A confirmação exige digitar a palavra RESTAURAR."
3. `docs/adr/0003-distribuicao-com-postgresql-embutido.md`, em "Consequências": troque "Atualizar a versão maior do PostgreSQL exige um procedimento de dump e restore. Será documentado em uma ADR própria antes da primeira distribuição." por "Atualizar a versão maior do PostgreSQL pode ser feito com o backup lógico (ADR-0014): exportar na versão antiga e restaurar na nova." E troque "Backup e restauração passam a ser responsabilidade do sistema (caso de uso UC19)." por "Backup e restauração passam a ser responsabilidade do sistema (caso de uso UC19 e ADR-0014)."

## Roteiro de teste manual

1. Como admin, exporte um backup: o arquivo `.zip` aparece no local escolhido e abre em qualquer descompactador, com os 9 arquivos.
2. Faça uma venda nova e restaure o backup digitando `RESTAURAR`: o sistema volta ao login e, depois de entrar, a venda nova não existe mais.
3. Tente restaurar digitando `restaurar` (minúsculo): a restauração é cancelada.
4. Tente restaurar um `.zip` qualquer: aparece "O arquivo escolhido não é um backup do Sells.".

## Critérios de aceite

1. `./mvnw -B verify` termina com `BUILD SUCCESS`, incluindo `BackupRoundTripTest` e `BackupServiceTest`.
2. O roteiro manual passa (ou fica registrado no relatório para validação da pessoa responsável).

## Commit

```
feat(backup): export and restore logical backups with COPY
```
