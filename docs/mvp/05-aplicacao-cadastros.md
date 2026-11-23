# Passo 05: Camada de aplicação, parte 1 (fundação e cadastros)

## Objetivo

Criar as portas de saída, a sessão do usuário, as exceções de aplicação e os serviços dos casos de uso UC01 a UC07, testados com repositórios em memória. Nenhum banco é usado neste passo.

## Pré-requisitos

Passo 04 concluído.

## Fora do escopo

- Vendas e estoque (passo 06).
- Implementações reais das portas (passo 07).
- Anotações do Spring: a camada `application` não pode importar nada do Spring. Os serviços são instanciados por uma `@Configuration` no passo 07.

## Convenções da camada

1. Pacote raiz: `com.github.svitorz.sells.application`.
2. Cada serviço é uma classe `public final`, com dependências recebidas pelo construtor.
3. Todo método público de serviço segue esta ordem:
   1. Verifica a sessão (`session.require()` ou `session.requireAdmin()`).
   2. Executa o trabalho dentro de `tx.inTransaction(...)`, inclusive as leituras.
   3. Retorna **views** (records da camada `application`), nunca objetos de domínio.
4. Comandos (dados de entrada) e views (dados de saída) são `record`s públicos no mesmo pacote do serviço.
5. O tempo vem sempre de `LocalDateTime.now(clock)`, com o `java.time.Clock` recebido no construtor.
6. Strings de busca: `null` ou em branco significam "sem filtro". Faça `trim()` antes de repassar para o repositório.

## Tarefas

### 5.1 Exceções (`application.common`)

Crie as quatro classes, todas `extends RuntimeException`, com construtor `(String message)`:

| Classe | Uso | Mensagem típica |
|---|---|---|
| `AccessDeniedException` | Sem sessão, ou sem permissão | `Faça login para continuar.` / `Somente administradores podem executar esta ação.` |
| `AuthenticationException` | Falha de login | `Usuário ou senha inválidos.` |
| `NotFoundException` | Registro inexistente | `Produto não encontrado.` (varia por entidade) |
| `ConcurrentUpdateException` | Conflito de versão (lançada pelo adaptador, no passo 07) | `Este registro foi alterado por outra operação. Feche e abra a tela novamente.` |

### 5.2 Sessão (`application.session`)

```java
package com.github.svitorz.sells.application.session;

import com.github.svitorz.sells.domain.user.Role;

public record AuthenticatedUser(long id, String name, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
```

```java
package com.github.svitorz.sells.application.session;

import com.github.svitorz.sells.application.common.AccessDeniedException;

import java.util.Optional;

/** The user logged in on this desktop. One instance per application. */
public final class SessionContext {

    private volatile AuthenticatedUser current;

    public Optional<AuthenticatedUser> current() {
        return Optional.ofNullable(current);
    }

    public AuthenticatedUser require() {
        AuthenticatedUser user = current;
        if (user == null) {
            throw new AccessDeniedException("Faça login para continuar.");
        }
        return user;
    }

    public AuthenticatedUser requireAdmin() {
        AuthenticatedUser user = require();
        if (!user.isAdmin()) {
            throw new AccessDeniedException("Somente administradores podem executar esta ação.");
        }
        return user;
    }

    public void start(AuthenticatedUser user) {
        this.current = user;
    }

    public void end() {
        this.current = null;
    }
}
```

### 5.3 Portas de saída (`application.port`)

Crie exatamente estas interfaces. As do passo 06 (`SaleRepository` e `StockMovementRepository`) também são criadas **agora**, para que os fakes fiquem completos, mas sem uso ainda.

```java
public interface TransactionRunner {
    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
```

> Os dois métodos têm o mesmo nome. Se a chamada com lambda ficar ambígua para o compilador, renomeie o método `default` para `runInTransaction` e use esse nome em todo o código.

```java
public interface PasswordHasher {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String hash);
}

public interface UserRepository {
    Optional<User> findById(long id);
    Optional<User> findByUsername(String username);        // exact match, username already lowercase
    List<User> findAll();                                  // ordered by name
    boolean existsByEmail(String email, Long excludingId); // excludingId may be null
    boolean existsByUsername(String username, Long excludingId);
    boolean existsActiveUser();
    long countActiveAdmins();
    Map<Long, String> findNamesByIds(Collection<Long> ids);
    User save(User user);                                  // returns the saved state, with id
}

public interface CategoryRepository {
    Optional<Category> findById(long id);
    List<Category> findAll();                              // ordered by name
    List<Category> findAllByIds(Collection<Long> ids);
    long countActiveProducts(long categoryId);             // products with deleted_at null
    Category save(Category category);
    void deleteById(long id);
}

public interface ProductRepository {
    Optional<Product> findActiveById(long id);
    Optional<Product> findActiveByBarcode(String barcode);
    /** Active products whose name contains term (case-insensitive) or whose barcode equals term. */
    List<Product> search(String term, Long categoryId, int limit);   // ordered by name
    boolean existsActiveByBarcode(String barcode, Long excludingId);
    List<Product> findAllByIds(Collection<Long> ids);                // includes deleted products
    /** Same as findAllByIds, but locks the rows (SELECT ... FOR UPDATE), ordered by id. */
    List<Product> findAllByIdsForUpdate(Collection<Long> ids);
    Product save(Product product);
}

public interface CustomerRepository {
    Optional<Customer> findActiveById(long id);
    /** Active customers whose name or phone contains term, or whose cpf_cnpj starts with the digits of term. */
    List<Customer> search(String term, int limit);                   // ordered by name
    boolean existsActiveByCpfCnpj(String cpfCnpj, Long excludingId);
    Map<Long, String> findNamesByIds(Collection<Long> ids);          // includes deleted customers
    Customer save(Customer customer);
}

public interface SaleRepository {
    Optional<Sale> findById(long id);
    Optional<Sale> findLatestDraftByUser(long userId);               // highest id with status DRAFT
    /**
     * Sales matching every non-null criterion, ordered by date desc, id desc.
     * from/to are inclusive and compare the date part of sale.date.
     * A null status means COMPLETED or CANCELLED (drafts are never part of the history).
     */
    List<Sale> search(SaleSearch criteria, int limit);
    Sale save(Sale sale);
    void deleteById(long id);
}

public interface StockMovementRepository {
    List<StockMovement> saveAll(List<StockMovement> movements);      // returns the saved movements, with ids, in the same order
    List<StockMovement> findByProductId(long productId);             // ordered by createdAt asc, id asc
}
```

Use `java.util.*`, `java.util.function.Supplier` e as classes do domínio nos imports. O record `SaleSearch` fica em `application.sale` e é definido no passo 06. Crie-o já agora, com exatamente esta forma:

```java
package com.github.svitorz.sells.application.sale;

public record SaleSearch(LocalDate from, LocalDate to, SaleStatus status, Long customerId, Long userId) {
}
```

### 5.4 Usuários (`application.user`) — UC01, UC02, UC03

Records:

```java
public record NewUserCommand(String name, String email, String username, String password, Role role, LocalDate birthdayDate) {}
public record UpdateUserCommand(String name, String email, String username, Role role, LocalDate birthdayDate) {}
public record UserView(long id, String name, String email, String username, Role role, LocalDate birthdayDate, boolean active) {}
public record UserOption(long id, String name) {}
```

`UserService(UserRepository users, PasswordHasher hasher, SessionContext session, TransactionRunner tx)`:

| Método | Sessão | Comportamento |
|---|---|---|
| `boolean needsFirstAdmin()` | nenhuma | `!users.existsActiveUser()` |
| `AuthenticatedUser createFirstAdmin(NewUserCommand cmd)` | nenhuma | Se `!needsFirstAdmin()`, lança `DomainException("O administrador inicial já foi configurado.")`. Ignora `cmd.role()` e usa `ADMIN`. Valida a senha, verifica a unicidade, salva e **inicia a sessão** com o usuário criado. Retorna o `AuthenticatedUser`. |
| `AuthenticatedUser login(String username, String password)` | nenhuma | Normaliza o username (`trim`, minúsculas, com `null` virando `""`). Se o usuário não existir, estiver inativo ou a senha não conferir (`hasher.matches`), lança `AuthenticationException("Usuário ou senha inválidos.")`, a **mesma mensagem nos três casos**. Em caso de sucesso, chama `session.start(...)` e retorna o `AuthenticatedUser`. |
| `void logout()` | nenhuma | `session.end()` |
| `List<UserView> listUsers()` | admin | Todos, ativos e inativos, ordenados por nome |
| `List<UserOption> listUserOptions()` | qualquer | Todos os usuários (id e nome), ordenados por nome. Usado nos filtros do histórico. |
| `UserView createUser(NewUserCommand cmd)` | admin | Valida a senha e a unicidade, e salva |
| `UserView updateUser(long id, UpdateUserCommand cmd)` | admin | Carrega o usuário (`NotFoundException("Usuário não encontrado.")`). Se ele é admin ativo, o novo role é `USER` e `users.countActiveAdmins() == 1`, lança `DomainException("Não é possível remover o último administrador ativo.")`. Verifica a unicidade excluindo o próprio id e salva. |
| `void changePassword(long id, String newPassword)` | admin | Valida a senha, `user.changePasswordHash(hasher.hash(...))` e salva |
| `void deactivateUser(long id)` | admin | Se `id == session.require().id()`, lança `DomainException("Você não pode desativar o seu próprio usuário.")`. Se o usuário é admin ativo e `countActiveAdmins() == 1`, lança a mensagem do último administrador. Desativa e salva. |
| `void activateUser(long id)` | admin | Ativa e salva |

Regras auxiliares (métodos privados):

- **Senha:** `null` ou com menos de 6 caracteres lança `DomainException("A senha deve ter pelo menos 6 caracteres.")`. A senha **não** recebe `trim`.
- **Unicidade:** normalize o e-mail e o username como o domínio faz (`trim` e minúsculas) antes de consultar. Se `existsByEmail`, lança `DomainException("Já existe um usuário com este e-mail.")`. Se `existsByUsername`, lança `DomainException("Já existe um usuário com este nome de usuário.")`.
- A ordem das validações em `createUser` e `createFirstAdmin` é: senha, depois `User.create(...)` (que valida os formatos), depois unicidade e, por fim, `save`.

### 5.5 Categorias (`application.catalog`) — UC04

```java
public record CategoryCommand(String name, String description) {}
public record CategoryView(long id, String name, String description) {}
```

`CategoryService(CategoryRepository categories, SessionContext session, TransactionRunner tx)`:

| Método | Sessão | Comportamento |
|---|---|---|
| `List<CategoryView> list()` | qualquer | Todas, ordenadas por nome |
| `CategoryView create(CategoryCommand cmd)` | admin | `Category.create` e salva |
| `CategoryView update(long id, CategoryCommand cmd)` | admin | `NotFoundException("Categoria não encontrada.")` se não existir |
| `long countProducts(long id)` | admin | `categories.countActiveProducts(id)`. A UI usa esse valor no aviso antes de excluir. |
| `void delete(long id)` | admin | `NotFoundException` se não existir; depois `deleteById` (exclusão física, ADR-0006) |

### 5.6 Produtos (`application.catalog`) — UC05, UC06

```java
public record CreateProductCommand(String name, String barcode, BigDecimal price, ProductUnit unit,
                                   String description, BigDecimal initialStock, Set<Long> categoryIds) {}
public record UpdateProductCommand(String name, String barcode, BigDecimal price, ProductUnit unit,
                                   String description, Set<Long> categoryIds) {}
public record ProductView(long id, String name, String barcode, BigDecimal price, ProductUnit unit,
                          String description, BigDecimal stock, List<CategoryView> categories) {}
```

`ProductService(ProductRepository products, CategoryRepository categories, StockMovementRepository movements, SessionContext session, TransactionRunner tx, Clock clock)`. A constante `SEARCH_LIMIT = 200`.

| Método | Sessão | Comportamento |
|---|---|---|
| `List<ProductView> search(String term, Long categoryId)` | qualquer | `products.search(term, categoryId, SEARCH_LIMIT)` |
| `Optional<ProductView> findByBarcode(String barcode)` | qualquer | Barcode com `trim`; se estiver em branco, retorna vazio |
| `ProductView get(long id)` | qualquer | `NotFoundException("Produto não encontrado.")` |
| `ProductView create(CreateProductCommand cmd)` | admin | Ver abaixo |
| `ProductView update(long id, UpdateProductCommand cmd)` | admin | Carrega o ativo (`NotFoundException`), verifica as categorias e a unicidade do barcode (excluindo o próprio id), `product.update(...)` e salva |
| `void delete(long id)` | admin | Carrega o ativo (`NotFoundException`), `product.delete(now)` e salva |

`create`, dentro de uma única transação:

1. `Product.create(...)`, que valida tudo e começa com estoque 0.
2. Se o barcode normalizado (`product.getBarcode()`) não for nulo e `existsActiveByBarcode(barcode, null)`, lança `DomainException("Já existe um produto ativo com este código de barras.")`.
3. Verifica as categorias: `categories.findAllByIds(ids).size() != ids.size()` lança `NotFoundException("Categoria não encontrada.")`.
4. `saved = products.save(product)`.
5. Se `initialStock` não for nulo e for maior que zero:
   1. `qty = saved.getUnit().stock(initialStock)`.
   2. `saved.applyStockChange(qty, now)`.
   3. `saved = products.save(saved)`.
   4. `movements.saveAll(List.of(StockMovement.entry(saved.getId(), qty, actor.id(), "Estoque inicial", now)))`.
   
   Se `initialStock` for negativo, `unit.stock` já rejeita com a mensagem do domínio. Faça essa validação **antes** do passo 4, chamando `cmd.unit().stock(...)` logo depois do passo 1 quando `initialStock != null`.
6. Retorna a view.

Montagem da `ProductView`: as categorias vêm de `categories.findAllByIds(product.getCategoryIds())`, ordenadas por nome. Em `search`, para evitar N consultas, junte os ids de categoria de todos os produtos, faça **uma** chamada a `findAllByIds` e distribua o resultado.

### 5.7 Clientes (`application.customer`) — UC07

```java
public record AddressData(String street, String number, String complement, String neighborhood,
                          String city, String state, String zipCode) {
    public static AddressData empty() { return new AddressData(null, null, null, null, null, null, null); }
}
public record CustomerCommand(String name, String phone, String email, LocalDate birthdayDate,
                              String cpfCnpj, AddressData address) {}
public record CustomerView(long id, String name, String phone, String email, LocalDate birthdayDate,
                           String cpfCnpj, AddressData address) {}
```

`CustomerService(CustomerRepository customers, SessionContext session, TransactionRunner tx, Clock clock)`. A constante `SEARCH_LIMIT = 200`. **Todos os métodos exigem apenas sessão** (operador também gerencia clientes, UC07).

| Método | Comportamento |
|---|---|
| `List<CustomerView> search(String term)` | `customers.search(term, SEARCH_LIMIT)` |
| `CustomerView get(long id)` | `NotFoundException("Cliente não encontrado.")` |
| `CustomerView create(CustomerCommand cmd)` | Converte `AddressData` para `Address` (com `null` virando `Address.empty()`), `Customer.create(...)`. Se o CPF/CNPJ normalizado não for nulo e `existsActiveByCpfCnpj(cpf, null)`, lança `DomainException("Já existe um cliente ativo com este CPF/CNPJ.")`. Salva. |
| `CustomerView update(long id, CustomerCommand cmd)` | Igual, excluindo o próprio id na unicidade |
| `void delete(long id)` | Exclusão lógica |

## Testes obrigatórios

### Fakes (em `src/test/java/com/github/svitorz/sells/application/fakes/`)

Crie um fake em memória para cada porta: `InMemoryUserRepository`, `InMemoryCategoryRepository`, `InMemoryProductRepository`, `InMemoryCustomerRepository`, `InMemorySaleRepository`, `InMemoryStockMovementRepository`, `FakePasswordHasher` e `DirectTransactionRunner`. Regras:

1. Cada repositório guarda os objetos em um `LinkedHashMap<Long, T>` e gera ids sequenciais começando em 1 (`AtomicLong`).
2. `save` de um objeto com `id == null` cria uma cópia com o novo id usando o `restore(...)` do domínio, guarda essa cópia e a retorna. Com `id != null`, apenas guarda e retorna o próprio objeto.
3. `version`: no `save`, o fake grava `version + 1` (ou `0` quando a versão for `null`), recriando o objeto com `restore`. Isso imita o JPA.
4. As consultas filtram exatamente como descrito no Javadoc de cada porta. As que dependem de produtos (como `countActiveProducts` em categorias) recebem o `InMemoryProductRepository` no construtor.
5. `FakePasswordHasher.hash(raw)` retorna `"hashed:" + raw`, e `matches` compara com isso.
6. `DirectTransactionRunner` apenas executa o `Supplier`.
7. `InMemorySaleRepository` e `InMemoryStockMovementRepository` devem estar completos, porque o passo 06 vai usá-los.

Crie `MutableClock` (no mesmo pacote `fakes`): uma classe que estende `java.time.Clock`, com zona `ZoneOffset.UTC`, começa em `Instant.parse("2026-01-15T13:00:00Z")` e tem os métodos `void set(Instant instant)` e `void advance(Duration duration)`. Ela permite que os testes do passo 06 criem vendas em datas diferentes.

Crie também `ApplicationTestFixture`, que instancia todos os fakes, um `SessionContext`, um `MutableClock` e todos os serviços deste passo, além de métodos auxiliares:

```java
AuthenticatedUser loginAsAdmin();     // creates (if needed) and logs in user "admin" / "secret123"
AuthenticatedUser loginAsOperator();  // creates (if needed) and logs in user "caixa" / "secret123", role USER
```

### Casos de teste (em `src/test/java/com/github/svitorz/sells/application/...`)

`UserServiceTest`:

1. `needsFirstAdmin` é `true` com o repositório vazio e `false` depois de `createFirstAdmin`.
2. `createFirstAdmin` força `ADMIN` mesmo com `role = USER` no comando, e inicia a sessão.
3. Um segundo `createFirstAdmin` é rejeitado.
4. `login` com a senha errada, com um usuário inexistente e com um usuário inativo lança `AuthenticationException` com a mesma mensagem.
5. `login` ignora maiúsculas e espaços no username (`"  Admin "`).
6. `createUser` sem sessão lança `AccessDeniedException("Faça login para continuar.")`; com operador logado, lança `AccessDeniedException("Somente administradores podem executar esta ação.")`.
7. `createUser` com a senha `"12345"` é rejeitado.
8. E-mail duplicado e username duplicado são rejeitados, inclusive quando o existente está inativo.
9. Rebaixar o último admin para `USER` é rejeitado. Rebaixar um admin quando há dois é aceito.
10. `deactivateUser`: (a) o admin A logado tenta desativar a si mesmo, o que é rejeitado com `Você não pode desativar o seu próprio usuário.`; (b) com os admins A e B ativos e A logado, desativar B é aceito.
11. `changePassword` permite login com a nova senha.

`CategoryServiceTest`: CRUD; `countProducts` conta só produtos ativos; operador não pode criar.

`ProductServiceTest`:

1. `create` com `initialStock = 5` resulta em estoque `5.000` e **uma** movimentação `ENTRY` de `5.000` com notes `Estoque inicial`.
2. `create` com `initialStock = 0` ou `null` não cria movimentação.
3. `create` com `initialStock = -1` é rejeitado e **nada** é salvo.
4. Barcode duplicado entre ativos é rejeitado. Depois de excluir o primeiro, o mesmo barcode é aceito.
5. Categoria inexistente gera `NotFoundException`.
6. `search` por parte do nome sem diferenciar maiúsculas (`"arr"` encontra `"Arroz"`), por barcode exato, por categoria; produtos excluídos não aparecem.
7. `update` não altera o estoque.
8. Operador pode usar `search`, mas não pode usar `create`.

`CustomerServiceTest`:

1. Operador consegue criar, editar e excluir.
2. CPF duplicado entre ativos é rejeitado, e depois de excluir o primeiro, o CPF é aceito.
3. `search` encontra por parte do nome, por parte do telefone e pelo início do CPF digitado com pontuação (`"123.456"`).
4. O endereço é retornado normalizado (UF em maiúsculas).

## Documentação a atualizar

`docs/use-cases.md`:

1. UC01, em "Regras", acrescente: "A mensagem de erro é a mesma para usuário inexistente, inativo ou senha incorreta."
2. UC03, em "Regras", acrescente: "A senha deve ter pelo menos 6 caracteres." e "Um administrador não pode desativar o próprio usuário."
3. UC05, em "Campos", troque "descrição" por "descrição (opcional)" e acrescente às regras: "O estoque inicial, quando maior que zero, é registrado como uma movimentação `ENTRY` com a observação `Estoque inicial`."

## Critérios de aceite

1. `./mvnw -B verify` termina com `BUILD SUCCESS`, incluindo o `ArchitectureTest`.
2. `grep -rn "org.springframework\|jakarta\|lombok" src/main/java/com/github/svitorz/sells/application` não retorna nada.

## Commit

```
feat(application): add ports, session and registration use cases
```
