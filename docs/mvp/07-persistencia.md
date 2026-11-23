# Passo 07: Adaptadores de persistência, transação e hash de senha

## Objetivo

Implementar as portas do passo 05 com JPA/Hibernate sobre o PostgreSQL, instanciar os serviços no Spring e provar, com testes de integração, que as regras de venda e estoque funcionam no banco real, inclusive sob concorrência. Resolve D8, D9 e D13.

## Pré-requisitos

Passo 06 concluído.

## Fora do escopo

Interface gráfica e backup.

## Tarefas

### 7.1 Ajustes na migration V1 (permitidos antes da primeira release, ADR-0011)

No arquivo `src/main/resources/db/migration/V1__CREATE_INITIAL_SCHEMA.sql`:

1. Na tabela `products`, troque `description TEXT not null,` por `description TEXT,`.
2. No fim do arquivo, acrescente:
   ```sql
   create index ix_sales_status_date on sales(status, date);
   create index ix_sales_user_status on sales(user_id, status);
   create index ix_stock_movements_product_created on stock_movements(product_id, created_at);
   ```

> Quem já rodou a aplicação localmente precisa apagar `~/.sells/pgdata`, porque o checksum da V1 mudou (ADR-0011, item 5).

### 7.2 Remover a auditoria do Spring Data

1. Apague `infrastructure/config/JpaConfig.java` e `infrastructure/persistence/jpa/entity/AuditableEntity.java`.
2. As datas passam a vir do domínio (passo 04).

### 7.3 Reescrever as entidades JPA

Pacote `com.github.svitorz.sells.infrastructure.persistence.jpa.entity`. Reescreva cada arquivo **por inteiro** seguindo esta especificação. Em todas as entidades:

- Use as anotações Lombok `@Getter`, `@Setter` e `@NoArgsConstructor`. Não use `@Builder`, `@SuperBuilder`, `@AllArgsConstructor` nem `@Data`.
- O id é `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;`.
- Enums são `@Enumerated(EnumType.STRING)`.
- **Não** use `@SQLDelete`, `@SQLRestriction`, `@Where`, `@EntityListeners` nem anotações `jakarta.validation`.
- Chaves estrangeiras para outros agregados são **colunas `Long` simples**, sem `@ManyToOne`. Isso mantém os agregados independentes e o mapeamento simples.

| Entidade | Tabela | Campos (nome Java → coluna) |
|---|---|---|
| `UserEntity` | `users` | `id`; `name` (length 100, not null); `email` (100, not null); `username` (100, not null); `password` (255, not null); `role` (`Role`, length 50, not null); `birthdayDate` → `birthday_date` (`LocalDate`); `active` (`boolean`, not null) |
| `CategoryEntity` | `categories` | `id`; `name` (255, not null); `description` (`columnDefinition = "TEXT"`). **Sem** coleção de produtos. |
| `ProductEntity` | `products` | `id`; `name` (255, not null); `barcode` (50); `price` (`BigDecimal`, precision 10, scale 2, not null); `unit` (`ProductUnit`, length 2, not null); `description` (TEXT); `stock` (`BigDecimal`, 10, 3, not null); `version` (`@Version Long`); `createdAt`, `updatedAt`, `deletedAt` (`LocalDateTime`); `categories`: `@ManyToMany(fetch = FetchType.LAZY) @JoinTable(name = "product_category", joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "category_id")) private Set<CategoryEntity> categories = new HashSet<>();` |
| `AddressEmbeddable` | (embutido) | `street` (150), `number` (20), `complement` (100), `neighborhood` (100), `city` (100), `state` (2), `zipCode` → `zip_code` (10) |
| `CustomerEntity` | `customers` | `id`; `name` (255, not null); `phone` (20, not null); `email` (255); `birthdayDate` → `birthday_date`; `cpfCnpj` → `cpf_cnpj` (14); `@Embedded AddressEmbeddable address`; `createdAt`, `updatedAt`, `deletedAt` |
| `SaleEntity` | `sales` | `id`; `date` (`LocalDateTime`, not null); `subtotal` (10, 2, not null); `status` (`SaleStatus`, length 20, not null); `customerId` → `customer_id` (`Long`); `userId` → `user_id` (`Long`, not null); `createdAt`, `updatedAt`, `cancelledAt` (`LocalDateTime`); `cancelledByUserId` → `cancelled_by_user_id` (`Long`); `cancelReason` → `cancel_reason` (TEXT); `version` (`@Version Long`); `items`: `@OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("id") @BatchSize(size = 100) private List<SaleItemEntity> items = new ArrayList<>();` |
| `SaleItemEntity` | `sale_items` | `id`; `sale`: `@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sale_id", nullable = false) SaleEntity sale`; `productId` → `product_id` (`Long`, not null); `quantity` (10, 3, not null); `unitPrice` → `unit_price` (10, 2, not null) |
| `StockMovementEntity` | `stock_movements` | `id`; `productId` → `product_id` (`Long`, not null); `type` (`StockMovementType`, length 20, not null); `quantity` (10, 3, not null); `saleId` → `sale_id` (`Long`); `userId` → `user_id` (`Long`, not null); `createdAt` → `created_at` (not null); `notes` (TEXT) |

`@BatchSize` é `org.hibernate.annotations.BatchSize`, e só é permitida neste pacote.

### 7.4 Repositórios Spring Data

Pacote `com.github.svitorz.sells.infrastructure.persistence.jpa.repository`. Todos estendem `JpaRepository<XEntity, Long>`. Os de produto, cliente e venda também estendem `JpaSpecificationExecutor<XEntity>`.

```java
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    List<UserEntity> findAllByOrderByNameAsc();
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByUsername(String username);
    boolean existsByUsernameAndIdNot(String username, Long id);
    boolean existsByActiveTrue();
    long countByRoleAndActiveTrue(Role role);
}

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, Long> {
    List<CategoryEntity> findAllByOrderByNameAsc();

    @Query(value = "select count(*) from product_category pc join products p on p.id = pc.product_id "
            + "where pc.category_id = :categoryId and p.deleted_at is null", nativeQuery = true)
    long countActiveProducts(@Param("categoryId") long categoryId);
}

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long>, JpaSpecificationExecutor<ProductEntity> {
    Optional<ProductEntity> findByIdAndDeletedAtIsNull(Long id);
    Optional<ProductEntity> findByBarcodeAndDeletedAtIsNull(String barcode);
    boolean existsByBarcodeAndDeletedAtIsNull(String barcode);
    boolean existsByBarcodeAndDeletedAtIsNullAndIdNot(String barcode, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductEntity p where p.id in :ids order by p.id")
    List<ProductEntity> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);
}

public interface CustomerJpaRepository extends JpaRepository<CustomerEntity, Long>, JpaSpecificationExecutor<CustomerEntity> {
    Optional<CustomerEntity> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByCpfCnpjAndDeletedAtIsNull(String cpfCnpj);
    boolean existsByCpfCnpjAndDeletedAtIsNullAndIdNot(String cpfCnpj, Long id);
}

public interface SaleJpaRepository extends JpaRepository<SaleEntity, Long>, JpaSpecificationExecutor<SaleEntity> {
    Optional<SaleEntity> findFirstByUserIdAndStatusOrderByIdDesc(Long userId, SaleStatus status);
}

public interface StockMovementJpaRepository extends JpaRepository<StockMovementEntity, Long> {
    List<StockMovementEntity> findByProductIdOrderByCreatedAtAscIdAsc(Long productId);
}
```

### 7.5 Adaptadores e mappers

Pacote `com.github.svitorz.sells.infrastructure.persistence.jpa.adapter`.

Para cada porta, crie um adaptador `@Repository` chamado `XRepositoryAdapter implements XRepository`, que recebe o repositório Spring Data no construtor. Para cada agregado, crie um mapper `final class XMapper`, package-private, com dois métodos estáticos:

- `static X toDomain(XEntity entity)`: usa `X.restore(...)`.
- `static void copyToEntity(X domain, XEntity entity)`: copia todos os campos, exceto `id` e `version`.

#### Regras de `save` (iguais para todos os adaptadores)

1. Se `domain.getId() == null`, crie uma entidade nova (`new XEntity()`) e chame `copyToEntity`.
2. Se o `id` não for nulo, carregue a entidade com `findById`. Se não existir, lance `IllegalStateException("X " + id + " not found")`.
3. **Apenas para `Product` e `Sale`**, que têm `@Version`: se `!Objects.equals(entity.getVersion(), domain.getVersion())`, lance `ConcurrentUpdateException("Este registro foi alterado por outra operação. Feche e abra a tela novamente.")`.
4. `copyToEntity(domain, entity)`.
5. `saved = jpaRepository.saveAndFlush(entity)`. O flush garante que o id e a versão retornados estejam atualizados.
6. Retorne `toDomain(saved)`.

#### Particularidades

- **Produto:** em `copyToEntity`, as categorias vêm de `categoryJpaRepository.findAllById(domain.getCategoryIds())`, transformadas em `HashSet`. Por isso, o adaptador de produto recebe também o `CategoryJpaRepository`, e a cópia das categorias é feita no adaptador, não no mapper estático. Em `toDomain`, `categoryIds` é o conjunto dos ids de `entity.getCategories()`.
- **Busca de produtos** (`search`), com Specification:
  ```java
  Specification<ProductEntity> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
  if (term != null && !term.isBlank()) {
      String like = "%" + term.trim().toLowerCase(Locale.ROOT) + "%";
      String exact = term.trim();
      spec = spec.and((root, query, cb) -> cb.or(
              cb.like(cb.lower(root.get("name")), like),
              cb.equal(root.get("barcode"), exact)));
  }
  if (categoryId != null) {
      spec = spec.and((root, query, cb) -> {
          query.distinct(true);
          return cb.equal(root.join("categories").get("id"), categoryId);
      });
  }
  return jpa.findAll(spec, PageRequest.of(0, limit, Sort.by("name"))).getContent()
          .stream().map(ProductMapper::toDomain).toList();
  ```
  Não use `Specification.where(...)`, que foi removido ou depreciado nas versões recentes do Spring Data.
- **Busca de clientes** (`search`): o mesmo padrão. Filtro base: `deletedAt is null`. Com o termo preenchido, faça um `or` entre `lower(name) like %termo%`, `phone like %termo%` e, **somente se** `Texts.digitsOnly(term)` não for nulo, `cpfCnpj like <dígitos>%`. Ordene por `name` e limite com `PageRequest.of(0, limit, Sort.by("name"))`.
- **Venda**, em `toDomain`: cada `SaleItem.restore(...)` precisa da unidade do produto. Use `toDomain(List<SaleEntity>)` com **uma** consulta: junte todos os `productId` dos itens, chame `productJpaRepository.findAllById(ids)` e monte um `Map<Long, ProductUnit>`. Use esse método também para uma única venda (`List.of(entity)`).
- **Venda**, em `save`, sincronize os itens por `productId` (nunca apague e recrie todos):
  ```java
  Map<Long, SaleItemEntity> byProduct = entity.getItems().stream()
          .collect(Collectors.toMap(SaleItemEntity::getProductId, Function.identity()));
  Set<Long> wanted = domain.getItems().stream().map(SaleItem::getProductId).collect(Collectors.toSet());
  entity.getItems().removeIf(item -> !wanted.contains(item.getProductId()));
  for (SaleItem item : domain.getItems()) {
      SaleItemEntity target = byProduct.get(item.getProductId());
      if (target == null) {
          target = new SaleItemEntity();
          target.setSale(entity);
          target.setProductId(item.getProductId());
          entity.getItems().add(target);
      }
      target.setQuantity(item.getQuantity());
      target.setUnitPrice(item.getUnitPrice());
  }
  ```
- **Busca de vendas** (`search`): Specification com `status` (se for `null`, use `root.get("status").in(SaleStatus.COMPLETED, SaleStatus.CANCELLED)`), `from` (`date >= from.atStartOfDay()`), `to` (`date < to.plusDays(1).atStartOfDay()`), `customerId` e `userId`. Use `PageRequest.of(0, limit, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")))`.
- `findLatestDraftByUser`: `findFirstByUserIdAndStatusOrderByIdDesc(userId, SaleStatus.DRAFT)`.
- `findAllByIdsForUpdate` e `findAllByIds` retornam lista vazia sem consultar o banco quando `ids` está vazio.
- `findNamesByIds`: `findAllById(ids)`, transformado em `Map<Long, String>` (id → nome).
- `StockMovementRepositoryAdapter.saveAll`: cria entidades novas, chama `saveAllAndFlush` e retorna os objetos convertidos, na mesma ordem.
- `existsByEmail(email, excludingId)` e equivalentes: se `excludingId == null`, use o método sem `IdNot`; caso contrário, use o método com `IdNot`.

### 7.6 Transação e hash de senha

`infrastructure/transaction/SpringTransactionRunner.java`:

```java
package com.github.svitorz.sells.infrastructure.transaction;

import com.github.svitorz.sells.application.common.ConcurrentUpdateException;
import com.github.svitorz.sells.application.port.TransactionRunner;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate template;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        try {
            return template.execute(status -> work.get());
        } catch (OptimisticLockingFailureException e) {
            throw new ConcurrentUpdateException(
                    "Este registro foi alterado por outra operação. Feche e abra a tela novamente.");
        }
    }
}
```

`infrastructure/security/BCryptPasswordHasher.java`: `@Component`, implementa `PasswordHasher` usando `new BCryptPasswordEncoder()` (de `spring-security-crypto`) em `hash` e `matches`.

### 7.7 Instanciar os serviços

`infrastructure/config/ApplicationServicesConfig.java`, anotada com `@Configuration`, com um método `@Bean` para cada objeto abaixo. Cada método recebe as dependências como parâmetros e chama `new`:

- `Clock clock()`, que retorna `Clock.systemDefaultZone()`.
- `SessionContext sessionContext()`.
- `UserService`, `CategoryService`, `ProductService`, `CustomerService`, `SaleService` e `StockService`, com exatamente os construtores definidos nos passos 05 e 06.

Os serviços **não** recebem anotações. Eles continuam livres de Spring.

## Testes obrigatórios

### Base

Crie `src/test/java/com/github/svitorz/sells/support/ServiceIntegrationTest.java`, que estende `IntegrationTest`, injeta (`@Autowired` em campo, permitido em testes) todos os serviços, o `SessionContext` e os seis adaptadores pelas interfaces das portas. Ele deve ter:

```java
@BeforeEach
void resetSession() {
    session.end();
}

protected AuthenticatedUser loginAsAdmin() {
    if (userService.needsFirstAdmin()) {
        return userService.createFirstAdmin(new NewUserCommand("Admin", "admin@example.com", "admin", "secret123", Role.ADMIN, null));
    }
    return userService.login("admin", "secret123");
}
```

### Testes de persistência (`src/test/java/com/github/svitorz/sells/infrastructure/persistence/`)

Todos estendem `ServiceIntegrationTest`.

| Classe | Casos mínimos |
|---|---|
| `UserPersistenceTest` | Ida e volta de todos os campos; `findByUsername`; `existsByEmail` com e sem `excludingId`; `countActiveAdmins` ignora inativos; `findNamesByIds` |
| `ProductPersistenceTest` | Ida e volta com 2 categorias; produto sem descrição é salvo (D13); `search` por nome (maiúsculas e minúsculas), por barcode e por categoria, sem duplicar o produto quando ele tem 2 categorias; produto excluído some de `search` e `findActiveById`, mas aparece em `findAllByIds`; duas cópias do mesmo produto: salvar a primeira e depois a segunda (versão antiga) lança `ConcurrentUpdateException`; `findAllByIdsForUpdate` retorna em ordem de id |
| `CustomerPersistenceTest` | Ida e volta com endereço completo e com endereço vazio; `search` por nome, telefone e prefixo do CPF; cliente excluído some de `search` |
| `SalePersistenceTest` | Ida e volta com 2 itens; adicionar um item, alterar a quantidade de outro e remover um terceiro em `save`s seguidos, conferindo as linhas de `sale_items` com `jdbcTemplate`; `deleteById` remove os itens; `findLatestDraftByUser`; `search` com cada filtro; **regressão do D9**: uma venda concluída cujo produto foi excluído depois continua carregando, com o item e a unidade corretos |
| `StockMovementPersistenceTest` | `saveAll` retorna ids não nulos; `findByProductId` retorna em ordem cronológica |

### Testes de fluxo (`src/test/java/com/github/svitorz/sells/application/SaleFlowIntegrationTest.java`)

Estende `ServiceIntegrationTest` e usa apenas os serviços, como a UI fará.

1. Produto com estoque 10, venda de 3: depois de `complete`, `select stock from products` retorna `7.000` e existe exatamente uma linha em `stock_movements` do tipo `SALE` com a quantidade `-3.000`.
2. **Rollback:** produto com estoque 1 e rascunho com 2 unidades. `complete` lança `DomainException`; no banco, o estoque continua `1.000`, o status da venda continua `DRAFT` e há zero movimentações `SALE`.
3. `cancel` depois do item 1: o estoque volta a `10.000` e existe uma movimentação `RETURN` de `3.000`.
4. **Concorrência:** produto com estoque 1; dois rascunhos diferentes, cada um com 1 unidade. Inicie dois threads que chamam `complete` ao mesmo tempo (use um `CountDownLatch` para a largada e um `ExecutorService` com 2 threads). Resultado esperado: exatamente uma chamada bem-sucedida e uma `DomainException` com a mensagem de estoque insuficiente; estoque final `0.000`; exatamente uma movimentação `SALE`. Repita o cenário 5 vezes no mesmo teste (com `@RepeatedTest(5)` ou um laço) para reduzir a chance de um falso positivo.
5. `ProductService.create` com estoque inicial 5 grava o estoque `5.000` e uma movimentação `ENTRY`.

### `SchemaConstraintsTest`

Acrescente o teste `allowsProductWithoutDescription`: `insert into products (name, price, unit, stock) values ('Pão', 1, 'UN', 1)` deve ser aceito.

## Documentação a atualizar

1. `docs/database.md`:
   - Em `products.description`: "Nulo: sim".
   - Nas colunas `created_at` e `updated_at`, troque "Preenchido pela auditoria" por "Preenchido pela aplicação".
   - Em `sales.date`, troque o texto por: "Data da venda. No rascunho, é a data de criação; na finalização, passa a ser a data da finalização."
   - Acrescente a seção "Índices", com os três índices criados em 7.1 e o motivo de cada um: histórico por status e data, rascunho aberto do usuário e movimentações por produto.
   - Na seção "Convenções", troque o item sobre `@SQLRestriction`, se houver, e acrescente: "Registros excluídos logicamente são filtrados explicitamente nas consultas do adaptador de persistência (`deleted_at IS NULL`)."
2. `docs/adr/0006-exclusao-logica-e-unicidade.md`:
   - Decisão, item 1: troque "pela anotação `@SQLRestriction`" por "por filtros explícitos nas consultas do adaptador de persistência".
   - Consequências, primeiro item: troque por "Consultas de produtos e clientes ativos filtram `deleted_at IS NULL` explicitamente. Consultas de histórico (itens de venda, movimentações) não filtram, para que produtos excluídos continuem visíveis em registros antigos. O `@SQLRestriction` não é usado porque esconderia esses produtos também no histórico."
3. `docs/adr/0005-estoque-movimentacoes-e-baixa-na-finalizacao.md`, item 7: troque "usa lock no produto ou controle de versão (`@Version`)" por "usa lock pessimista nas linhas dos produtos (`SELECT ... FOR UPDATE`, em ordem de id para evitar deadlock). O `@Version` protege as demais edições."

## Critérios de aceite

1. `./mvnw -B verify` termina com `BUILD SUCCESS`, incluindo o `ArchitectureTest` e o `SaleFlowIntegrationTest`.
2. `grep -rn "SQLRestriction\|SQLDelete\|EnableJpaAuditing\|AuditingEntityListener" src/` não retorna nada.
3. `grep -rln "lombok" src/main/java | grep -v "/persistence/jpa/entity/"` não retorna nada.

## Commit

```
feat(persistence): implement JPA adapters and wire application services
```
