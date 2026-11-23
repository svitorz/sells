# Passo 04: Modelo de domínio puro

## Objetivo

Criar as entidades de domínio com as regras de negócio de `DOMAIN.md`, `use-cases.md` e das ADRs, em Java puro e cobertas por testes unitários. Resolve a parte de regras de D5 e D8.

## Pré-requisitos

Passo 03 concluído.

## Fora do escopo

- Não altere as entidades JPA em `infrastructure`. A ligação entre as duas camadas é feita no passo 07.
- Não crie serviços nem repositórios (passos 05 e 06).

## Regras gerais do domínio

1. Nenhum import fora de `java.*` e `com.github.svitorz.sells.domain.*`. O `ArchitectureTest` vai falhar se isso acontecer.
2. Sem Lombok. Getters explícitos no estilo JavaBean (`getName()`, `isActive()`). **Sem setters públicos**: o estado muda apenas pelos métodos de negócio descritos aqui.
3. Toda classe com identidade tem dois construtores estáticos:
   - `create(...)`: valida e normaliza tudo. O `id` fica `null` até a entidade ser persistida.
   - `restore(...)`: usado apenas pelos adaptadores de persistência para reconstruir um objeto já salvo. Não valida regras de formato, apenas não nulidade dos campos obrigatórios (lance `NullPointerException` com `Objects.requireNonNull`).
4. Métodos que alteram estado recebem `LocalDateTime now` quando precisam registrar data. O domínio nunca chama `LocalDateTime.now()` diretamente.
5. Regras violadas lançam `DomainException` com a mensagem **em português**, exatamente como especificado aqui. Erros de programação (argumento nulo onde nunca deveria ser) lançam `IllegalArgumentException` ou `NullPointerException`, com a mensagem em inglês.
6. Classes `final`, com campos `private`. Os campos que nunca mudam são `final`.

## Tarefas

### 4.1 `domain.shared`

`DomainException.java`:

```java
package com.github.svitorz.sells.domain.shared;

/** A business rule was violated. The message is shown to the user, in Portuguese. */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
```

`Texts.java`:

```java
package com.github.svitorz.sells.domain.shared;

public final class Texts {

    private Texts() {
    }

    /** Trims the value and fails if it is blank or longer than maxLength. */
    public static String required(String value, String label, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new DomainException("O campo " + label + " é obrigatório.");
        }
        return checkLength(trimmed, label, maxLength);
    }

    /** Trims the value and returns null if it is blank. */
    public static String optional(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return checkLength(value.trim(), label, maxLength);
    }

    /** Removes every character that is not a digit. Returns null for null or for no digits. */
    public static String digitsOnly(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private static String checkLength(String value, String label, int maxLength) {
        if (value.length() > maxLength) {
            throw new DomainException("O campo " + label + " deve ter no máximo " + maxLength + " caracteres.");
        }
        return value;
    }
}
```

`Decimals.java`:

```java
package com.github.svitorz.sells.domain.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Decimals {

    /** Largest value that fits NUMERIC(10,2). */
    public static final BigDecimal MAX_MONEY = new BigDecimal("99999999.99");
    /** Largest value that fits NUMERIC(10,3). */
    public static final BigDecimal MAX_QUANTITY = new BigDecimal("9999999.999");

    private Decimals() {
    }

    /** Validates a money value (>= 0, at most 2 decimals) and returns it with scale 2. */
    public static BigDecimal money(BigDecimal value, String label) {
        if (value == null) {
            throw new DomainException("O campo " + label + " é obrigatório.");
        }
        if (value.signum() < 0) {
            throw new DomainException("O campo " + label + " não pode ser negativo.");
        }
        if (decimalPlaces(value) > 2) {
            throw new DomainException("O campo " + label + " aceita no máximo 2 casas decimais.");
        }
        if (value.compareTo(MAX_MONEY) > 0) {
            throw new DomainException("O campo " + label + " excede o valor máximo permitido.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Number of significant decimal places (2.500 has 1). Never negative. */
    public static int decimalPlaces(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    /** Formats for messages: 2.500 becomes "2,5" and 3.000 becomes "3". */
    public static String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
```

### 4.2 `domain.product.ProductUnit`

Substitua o enum atual por este, que concentra a regra de quantidade por unidade (ADR-0009):

```java
package com.github.svitorz.sells.domain.product;

import com.github.svitorz.sells.domain.shared.Decimals;
import com.github.svitorz.sells.domain.shared.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public enum ProductUnit {
    UN(0, "Produtos vendidos por unidade aceitam apenas quantidades inteiras."),
    KG(3, "Produtos vendidos por quilo aceitam no máximo 3 casas decimais.");

    private final int maxDecimals;
    private final String decimalsMessage;

    ProductUnit(int maxDecimals, String decimalsMessage) {
        this.maxDecimals = maxDecimals;
        this.decimalsMessage = decimalsMessage;
    }

    /** A quantity being sold or received: must be greater than zero. */
    public BigDecimal quantity(BigDecimal value) {
        BigDecimal normalized = normalize(value);
        if (normalized.signum() <= 0) {
            throw new DomainException("A quantidade deve ser maior que zero.");
        }
        return normalized;
    }

    /** A stock balance: must be zero or greater. */
    public BigDecimal stock(BigDecimal value) {
        BigDecimal normalized = normalize(value);
        if (normalized.signum() < 0) {
            throw new DomainException("O estoque não pode ser negativo.");
        }
        return normalized;
    }

    /** A stock difference (adjustment): positive or negative, never zero. */
    public BigDecimal delta(BigDecimal value) {
        BigDecimal normalized = normalize(value);
        if (normalized.signum() == 0) {
            throw new DomainException("A quantidade deve ser diferente de zero.");
        }
        return normalized;
    }

    private BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            throw new DomainException("Informe a quantidade.");
        }
        if (Decimals.decimalPlaces(value) > maxDecimals) {
            throw new DomainException(decimalsMessage);
        }
        if (value.abs().compareTo(Decimals.MAX_QUANTITY) > 0) {
            throw new DomainException("A quantidade excede o máximo permitido.");
        }
        return value.setScale(3, RoundingMode.UNNECESSARY);
    }
}
```

### 4.3 `domain.user.User`

Campos: `Long id`, `String name`, `String email`, `String username`, `String passwordHash`, `Role role`, `LocalDate birthdayDate` (opcional), `boolean active`.

```java
public static User create(String name, String email, String username, String passwordHash, Role role, LocalDate birthdayDate)
public static User restore(Long id, String name, String email, String username, String passwordHash, Role role, LocalDate birthdayDate, boolean active)
public void updateProfile(String name, String email, String username, Role role, LocalDate birthdayDate)
public void changePasswordHash(String passwordHash)
public void deactivate()
public void activate()
public boolean isAdmin()            // role == ADMIN
// getters: getId, getName, getEmail, getUsername, getPasswordHash, getRole, getBirthdayDate, isActive
```

Regras (aplicadas em `create` e `updateProfile`):

| Campo | Regra | Mensagem |
|---|---|---|
| name | `Texts.required(name, "Nome", 100)` | (da classe `Texts`) |
| email | `Texts.required(email, "E-mail", 100)`, depois `toLowerCase(Locale.ROOT)`, e deve casar com `^[^@\s]+@[^@\s]+\.[^@\s]+$` | `Informe um e-mail válido.` |
| username | `Texts.required(username, "Usuário", 100)`, depois `toLowerCase(Locale.ROOT)`, e deve casar com `^[a-z0-9._-]{3,}$` | `O usuário deve ter pelo menos 3 caracteres e usar apenas letras, números, ponto, hífen ou sublinhado.` |
| role | não nulo | `O campo Perfil é obrigatório.` |
| passwordHash | não nulo e não vazio (em `create` e `changePasswordHash`) | `IllegalArgumentException("passwordHash is required")` |

`create` define `active = true`. A regra do tamanho mínimo da senha fica na camada de aplicação, porque o domínio só recebe o hash.

### 4.4 `domain.product.Category`

Campos: `Long id`, `String name`, `String description`.

```java
public static Category create(String name, String description)
public static Category restore(Long id, String name, String description)
public void update(String name, String description)
// getters: getId, getName, getDescription
```

Regras: `name = Texts.required(name, "Nome", 255)` e `description = Texts.optional(description, "Descrição", 2000)`.

### 4.5 `domain.product.Product`

Campos: `Long id`, `String name`, `String barcode`, `BigDecimal price`, `ProductUnit unit`, `String description`, `BigDecimal stock`, `Set<Long> categoryIds`, `LocalDateTime createdAt`, `LocalDateTime updatedAt`, `LocalDateTime deletedAt`, `Long version`.

```java
public static Product create(String name, String barcode, BigDecimal price, ProductUnit unit,
                             String description, Set<Long> categoryIds, LocalDateTime now)
public static Product restore(Long id, String name, String barcode, BigDecimal price, ProductUnit unit,
                              String description, BigDecimal stock, Set<Long> categoryIds,
                              LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt, Long version)
public void update(String name, String barcode, BigDecimal price, ProductUnit unit,
                   String description, Set<Long> categoryIds, LocalDateTime now)
public void applyStockChange(BigDecimal delta, LocalDateTime now)
public BigDecimal normalizeQuantity(BigDecimal quantity)   // returns unit.quantity(quantity)
public void delete(LocalDateTime now)
public boolean isDeleted()                                  // deletedAt != null
// getters: getId, getName, getBarcode, getPrice, getUnit, getDescription, getStock,
//          getCategoryIds (unmodifiable copy), getCreatedAt, getUpdatedAt, getDeletedAt, getVersion
```

Regras:

| Situação | Regra | Mensagem |
|---|---|---|
| name | `Texts.required(name, "Nome", 255)` | — |
| barcode | `Texts.optional(barcode, "Código de barras", 50)`. Se não for nulo, não pode conter espaço em branco (`\s`) | `O código de barras não pode conter espaços.` |
| price | `Decimals.money(price, "Preço")` | — |
| unit | não nulo | `O campo Unidade é obrigatório.` |
| description | `Texts.optional(description, "Descrição", 2000)` | — |
| categoryIds | `null` vira conjunto vazio; guarde uma cópia (`Set.copyOf`) | — |
| create | `stock = 0.000` (`BigDecimal.ZERO.setScale(3)`), `createdAt = updatedAt = now`, `version = null` | — |
| update de produto excluído | proibido | `Produto excluído não pode ser alterado.` |
| update de `KG` para `UN` com estoque fracionado | proibido (use `Decimals.decimalPlaces(stock) > 0`) | `Não é possível mudar a unidade para UN enquanto o estoque tiver frações. Ajuste o estoque antes.` |
| update | define `updatedAt = now` | — |
| applyStockChange | `delta` normalizado por `unit.delta(delta)`; `novo = stock + delta`; se `novo < 0`, falha; senão `stock = novo` e `updatedAt = now` | `Estoque insuficiente para "<name>". Disponível: <Decimals.format(stock)>.` |
| delete de produto já excluído | proibido | `O produto já foi excluído.` |
| delete | `deletedAt = now`, `updatedAt = now` | — |

> `applyStockChange` **não** verifica se o produto está excluído. O cancelamento de uma venda antiga precisa devolver o estoque mesmo que o produto tenha sido excluído depois. A camada de aplicação impede entradas e ajustes em produtos excluídos.

### 4.6 `domain.customer.Address`

```java
public record Address(String street, String number, String complement, String neighborhood,
                      String city, String state, String zipCode) {

    public Address {
        street = Texts.optional(street, "Rua", 150);
        number = Texts.optional(number, "Número", 20);
        complement = Texts.optional(complement, "Complemento", 100);
        neighborhood = Texts.optional(neighborhood, "Bairro", 100);
        city = Texts.optional(city, "Cidade", 100);
        state = normalizeState(state);
        zipCode = normalizeZipCode(zipCode);
    }

    public static Address empty() {
        return new Address(null, null, null, null, null, null, null);
    }

    public boolean isEmpty() {
        return street == null && number == null && complement == null && neighborhood == null
                && city == null && state == null && zipCode == null;
    }

    // normalizeState: Texts.optional(state, "UF", 2) -> toUpperCase(Locale.ROOT); must match ^[A-Z]{2}$
    //                 otherwise DomainException("Informe a UF com 2 letras.")
    // normalizeZipCode: Texts.digitsOnly(zipCode); if not null it must have exactly 8 digits
    //                   otherwise DomainException("O CEP deve ter 8 dígitos.")
}
```

> Atenção: `Texts.optional(state, "UF", 2)` lança a mensagem de tamanho máximo para "São Paulo". Isso é aceitável.

### 4.7 `domain.customer.Customer`

Campos: `Long id`, `String name`, `String phone`, `String email`, `LocalDate birthdayDate`, `String cpfCnpj`, `Address address`, `LocalDateTime createdAt`, `LocalDateTime updatedAt`, `LocalDateTime deletedAt`.

```java
public static Customer create(String name, String phone, String email, LocalDate birthdayDate,
                              String cpfCnpj, Address address, LocalDateTime now)
public static Customer restore(Long id, String name, String phone, String email, LocalDate birthdayDate,
                               String cpfCnpj, Address address, LocalDateTime createdAt,
                               LocalDateTime updatedAt, LocalDateTime deletedAt)
public void update(String name, String phone, String email, LocalDate birthdayDate,
                   String cpfCnpj, Address address, LocalDateTime now)
public void delete(LocalDateTime now)
public boolean isDeleted()
// getters for every field
```

Regras (em `create` e `update`):

| Campo | Regra | Mensagem |
|---|---|---|
| name | `Texts.required(name, "Nome", 255)` | — |
| phone | `Texts.required(phone, "Telefone", 20)` | — |
| email | `Texts.optional(email, "E-mail", 255)`, minúsculo, mesmo padrão do `User` | `Informe um e-mail válido.` |
| birthdayDate | opcional; se `birthdayDate.isAfter(now.toLocalDate())`, falha | `A data de nascimento não pode estar no futuro.` |
| cpfCnpj | `Texts.digitsOnly(cpfCnpj)`; se não for nulo, deve ter 11 ou 14 dígitos | `O CPF deve ter 11 dígitos e o CNPJ, 14.` |
| address | `null` vira `Address.empty()` | — |
| update ou delete de cliente excluído | proibido | `Cliente excluído não pode ser alterado.` |

`create` define `createdAt = updatedAt = now`. `update` e `delete` definem `updatedAt = now`, e `delete` define também `deletedAt = now`. A validação dos dígitos verificadores do CPF ou CNPJ não faz parte do MVP (ADR-0009).

### 4.8 `domain.sale.SaleItem` e `domain.sale.Sale`

Implemente exatamente assim:

```java
package com.github.svitorz.sells.domain.sale;

import com.github.svitorz.sells.domain.product.ProductUnit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class SaleItem {

    private final Long id;
    private final long productId;
    private final ProductUnit unit;
    private BigDecimal quantity;
    private final BigDecimal unitPrice;

    private SaleItem(Long id, long productId, ProductUnit unit, BigDecimal quantity, BigDecimal unitPrice) {
        this.id = id;
        this.productId = productId;
        this.unit = Objects.requireNonNull(unit);
        this.quantity = Objects.requireNonNull(quantity);
        this.unitPrice = Objects.requireNonNull(unitPrice);
    }

    static SaleItem create(long productId, ProductUnit unit, BigDecimal quantity, BigDecimal unitPrice) {
        return new SaleItem(null, productId, unit, unit.quantity(quantity), unitPrice);
    }

    public static SaleItem restore(Long id, long productId, ProductUnit unit, BigDecimal quantity, BigDecimal unitPrice) {
        return new SaleItem(id, productId, unit, quantity, unitPrice);
    }

    void changeQuantity(BigDecimal newQuantity) {
        this.quantity = unit.quantity(newQuantity);
    }

    /** quantity x unitPrice, rounded to cents with HALF_UP. */
    public BigDecimal total() {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public Long getId() { return id; }
    public long getProductId() { return productId; }
    public ProductUnit getUnit() { return unit; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
```

```java
package com.github.svitorz.sells.domain.sale;

import com.github.svitorz.sells.domain.product.Product;
import com.github.svitorz.sells.domain.shared.Decimals;
import com.github.svitorz.sells.domain.shared.DomainException;
import com.github.svitorz.sells.domain.shared.Texts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Sale {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final Long id;
    private LocalDateTime date;
    private BigDecimal subtotal;
    private SaleStatus status;
    private Long customerId;
    private final long userId;
    private final List<SaleItem> items;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime cancelledAt;
    private Long cancelledByUserId;
    private String cancelReason;
    private final Long version;

    private Sale(Long id, LocalDateTime date, BigDecimal subtotal, SaleStatus status, Long customerId,
                 long userId, List<SaleItem> items, LocalDateTime createdAt, LocalDateTime updatedAt,
                 LocalDateTime cancelledAt, Long cancelledByUserId, String cancelReason, Long version) {
        this.id = id;
        this.date = Objects.requireNonNull(date);
        this.subtotal = Objects.requireNonNull(subtotal);
        this.status = Objects.requireNonNull(status);
        this.customerId = customerId;
        this.userId = userId;
        this.items = new ArrayList<>(items);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.cancelledAt = cancelledAt;
        this.cancelledByUserId = cancelledByUserId;
        this.cancelReason = cancelReason;
        this.version = version;
    }

    /** UC08: a new draft, owned by the given user, with subtotal zero. */
    public static Sale startDraft(long userId, LocalDateTime now) {
        return new Sale(null, now, ZERO, SaleStatus.DRAFT, null, userId, List.of(),
                now, now, null, null, null, null);
    }

    public static Sale restore(Long id, LocalDateTime date, BigDecimal subtotal, SaleStatus status,
                               Long customerId, long userId, List<SaleItem> items, LocalDateTime createdAt,
                               LocalDateTime updatedAt, LocalDateTime cancelledAt, Long cancelledByUserId,
                               String cancelReason, Long version) {
        return new Sale(id, date, subtotal, status, customerId, userId, items, createdAt, updatedAt,
                cancelledAt, cancelledByUserId, cancelReason, version);
    }

    /**
     * UC09: adds the product, or sums the quantity if it is already in the sale.
     * The unit price is copied from the product only when the line is created.
     */
    public SaleItem addItem(Product product, BigDecimal quantity, LocalDateTime now) {
        requireDraft();
        if (product.getId() == null) {
            throw new IllegalArgumentException("product must be persisted");
        }
        if (product.isDeleted()) {
            throw new DomainException("O produto \"" + product.getName() + "\" foi excluído e não pode ser vendido.");
        }
        BigDecimal normalized = product.normalizeQuantity(quantity);
        Optional<SaleItem> existing = findItem(product.getId());
        SaleItem item;
        if (existing.isPresent()) {
            item = existing.get();
            item.changeQuantity(item.getQuantity().add(normalized));
        } else {
            item = SaleItem.create(product.getId(), product.getUnit(), normalized, product.getPrice());
            items.add(item);
        }
        touch(now);
        return item;
    }

    /** UC10 */
    public void changeItemQuantity(long productId, BigDecimal quantity, LocalDateTime now) {
        requireDraft();
        requireItem(productId).changeQuantity(quantity);
        touch(now);
    }

    /** UC10 */
    public void removeItem(long productId, LocalDateTime now) {
        requireDraft();
        items.remove(requireItem(productId));
        touch(now);
    }

    /** UC11: null removes the customer. */
    public void assignCustomer(Long customerId, LocalDateTime now) {
        requireDraft();
        this.customerId = customerId;
        touch(now);
    }

    /** UC13: stock is handled by the application service before calling this. */
    public void complete(LocalDateTime now) {
        requireDraft();
        if (items.isEmpty()) {
            throw new DomainException("A venda precisa ter pelo menos um item para ser finalizada.");
        }
        status = SaleStatus.COMPLETED;
        date = now;
        touch(now);
    }

    /** UC14: stock is handled by the application service before calling this. */
    public void cancel(long cancelledByUserId, String reason, LocalDateTime now) {
        if (status != SaleStatus.COMPLETED) {
            throw new DomainException("Somente vendas concluídas podem ser canceladas.");
        }
        String trimmed = Texts.required(reason, "Motivo do cancelamento", 2000);
        status = SaleStatus.CANCELLED;
        cancelledAt = now;
        this.cancelledByUserId = cancelledByUserId;
        cancelReason = trimmed;
        updatedAt = now;
    }

    public void requireDraft() {
        if (status != SaleStatus.DRAFT) {
            throw new DomainException("Esta venda não está mais em rascunho e não pode ser alterada.");
        }
    }

    public boolean isDraft() {
        return status == SaleStatus.DRAFT;
    }

    public Optional<SaleItem> findItem(long productId) {
        return items.stream().filter(item -> item.getProductId() == productId).findFirst();
    }

    private SaleItem requireItem(long productId) {
        return findItem(productId).orElseThrow(() -> new DomainException("O item não está nesta venda."));
    }

    private void touch(LocalDateTime now) {
        BigDecimal total = items.stream().map(SaleItem::total).reduce(ZERO, BigDecimal::add);
        if (total.compareTo(Decimals.MAX_MONEY) > 0) {
            throw new DomainException("O total da venda excede o valor máximo permitido.");
        }
        subtotal = total;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public LocalDateTime getDate() { return date; }
    public BigDecimal getSubtotal() { return subtotal; }
    public SaleStatus getStatus() { return status; }
    public Long getCustomerId() { return customerId; }
    public long getUserId() { return userId; }
    public List<SaleItem> getItems() { return List.copyOf(items); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public Long getCancelledByUserId() { return cancelledByUserId; }
    public String getCancelReason() { return cancelReason; }
    public Long getVersion() { return version; }
}
```

> Observação: em `touch`, se o total ultrapassar o máximo, a exceção é lançada depois que o item já foi alterado em memória. Isso é aceitável, porque a camada de aplicação descarta o objeto quando há exceção (a transação é desfeita e a venda é recarregada do banco).

### 4.9 `domain.stock.StockMovement`

Campos (todos `final`): `Long id`, `long productId`, `StockMovementType type`, `BigDecimal quantity`, `Long saleId`, `long userId`, `LocalDateTime createdAt`, `String notes`.

```java
public static StockMovement entry(long productId, BigDecimal quantity, long userId, String notes, LocalDateTime now)
public static StockMovement adjustment(long productId, BigDecimal delta, long userId, String notes, LocalDateTime now)
public static StockMovement sale(long productId, BigDecimal quantity, long saleId, long userId, LocalDateTime now)
public static StockMovement saleReturn(long productId, BigDecimal quantity, long saleId, long userId, LocalDateTime now)
public static StockMovement restore(Long id, long productId, StockMovementType type, BigDecimal quantity,
                                    Long saleId, long userId, LocalDateTime createdAt, String notes)
// getters for every field
```

Regras. As quantidades recebidas já vêm normalizadas pela unidade do produto, e quem chama é responsável por isso.

| Fábrica | Quantidade gravada | `saleId` | `notes` | Falha |
|---|---|---|---|---|
| `entry` | `quantity` (deve ser > 0) | `null` | `Texts.optional(notes, "Observações", 2000)` | `quantity <= 0`: `DomainException("A quantidade de entrada deve ser maior que zero.")` |
| `adjustment` | `delta` (deve ser diferente de 0) | `null` | `Texts.required(notes, "Motivo do ajuste", 2000)` | `delta == 0`: `DomainException("A quantidade deve ser diferente de zero.")` |
| `sale` | `quantity.negate()` (a entrada deve ser > 0) | obrigatório | `null` | `quantity <= 0`: `IllegalArgumentException` |
| `saleReturn` | `quantity` (deve ser > 0) | obrigatório | `null` | `quantity <= 0`: `IllegalArgumentException` |

O enum `StockMovementType` continua como está.

## Testes obrigatórios

Crie os testes em `src/test/java/com/github/svitorz/sells/domain/...`, no mesmo subpacote da classe testada. Eles são testes JUnit 5 puros, sem Spring e sem banco. Use a constante `LocalDateTime NOW = LocalDateTime.of(2026, 1, 15, 10, 0)`. Para verificar mensagens, use `assertThrows(DomainException.class, ...)` e `assertEquals` na mensagem.

| Classe de teste | Casos mínimos |
|---|---|
| `DecimalsTest` | `money(10)` vira `10.00`; rejeita `null`, `-0.01`, `1.234` e `100000000`; `format(new BigDecimal("2.500"))` é `"2,5"` |
| `ProductUnitTest` | `UN.quantity(2)` vira `2.000`; `UN.quantity(1.5)` é rejeitado; `KG.quantity(0.375)` é aceito; `KG.quantity(0.3755)` é rejeitado; `quantity(0)` e `quantity(-1)` são rejeitados; `delta(-2)` é aceito; `delta(0)` é rejeitado; `stock(0)` é aceito; `stock(-1)` é rejeitado |
| `UserTest` | `create` faz trim e deixa e-mail e username em minúsculas; rejeita e-mail sem `@`; rejeita username `ab` e `ana maria`; rejeita nome vazio; rejeita nome com 101 caracteres; `deactivate` e `activate`; `isAdmin` |
| `CategoryTest` | Rejeita nome vazio; descrição em branco vira `null` |
| `ProductTest` | `create` começa com estoque `0.000` e datas iguais a `NOW`; barcode em branco vira `null`; barcode `"789 100"` é rejeitado; preço negativo é rejeitado; `applyStockChange` soma e subtrai; estoque insuficiente gera exatamente a mensagem `Estoque insuficiente para "Arroz". Disponível: 2.`; delta fracionado em `UN` é rejeitado; mudança de `KG` para `UN` com estoque `1.5` é rejeitada; `delete` preenche `deletedAt`; `delete` duas vezes é rejeitado; `update` depois de `delete` é rejeitado |
| `AddressTest` | `"sp"` vira `"SP"`; `"01310-100"` vira `"01310100"`; CEP `"123"` é rejeitado; UF `"S1"` é rejeitada; `empty().isEmpty()` é `true` |
| `CustomerTest` | `"123.456.789-01"` vira `"12345678901"`; `"12.345.678/0001-90"` vira `"12345678000190"`; `"123"` é rejeitado; data de nascimento amanhã é rejeitada; endereço `null` vira vazio; `update` depois de `delete` é rejeitado |
| `SaleTest` | Ver a tabela abaixo |
| `StockMovementTest` | `sale` grava quantidade negativa; `saleReturn` grava positiva; `adjustment` sem notes é rejeitado; `entry` com 0 é rejeitado; `adjustment` com 0 é rejeitado |

Casos obrigatórios de `SaleTest`. Crie produtos com `Product.restore(...)` e ids fixos.

1. `startDraft` tem status `DRAFT`, subtotal `0.00`, nenhum item e `customerId` nulo.
2. `addItem` copia o preço do produto para `unitPrice`.
3. Adicionar o mesmo produto duas vezes soma as quantidades em **uma** linha.
4. Ao adicionar o mesmo produto de novo depois que o preço do produto mudou, o `unitPrice` original é mantido.
5. Arredondamento: `0.125` kg a `9.99` dá subtotal `1.25`; `0.333` kg a `10.00` dá `3.33`; 3 un a `2.50` mais `0.5` kg a `7.99` dá `11.50` (`7.50 + 4.00`, porque `3.995` arredonda para `4.00`).
6. Quantidade fracionada para produto `UN` é rejeitada.
7. Produto excluído é rejeitado com a mensagem `O produto "X" foi excluído e não pode ser vendido.`
8. `changeItemQuantity` recalcula o subtotal; um item inexistente é rejeitado com `O item não está nesta venda.`
9. `removeItem` recalcula o subtotal.
10. `complete` sem itens é rejeitado; com itens, o status vira `COMPLETED` e `date` vira o `now` informado.
11. Depois de `complete`, `addItem`, `removeItem`, `changeItemQuantity` e `assignCustomer` lançam `Esta venda não está mais em rascunho e não pode ser alterada.`
12. `cancel` em rascunho é rejeitado; `cancel` com motivo `"   "` é rejeitado; `cancel` válido preenche `cancelledAt`, `cancelledByUserId` e `cancelReason`; um segundo `cancel` é rejeitado.

## Documentação a atualizar

1. `docs/DOMAIN.md`, na seção "Invariantes", acrescente:
   - "O total de um item é `quantity × unit_price`, arredondado para centavos com arredondamento comercial (meio para cima). O subtotal da venda é a soma dos totais dos itens."
   - "Ao adicionar um produto que já está na venda, a quantidade é somada e o preço unitário original da linha é mantido."
   - "Produtos `UN` aceitam quantidades inteiras. Produtos `KG` aceitam até 3 casas decimais."
   - "O CPF ou CNPJ é armazenado somente com dígitos. O CEP é armazenado somente com dígitos (8). A UF é armazenada em maiúsculas."
2. `docs/use-cases.md`, no UC09, troque a regra "Se o produto já estiver no rascunho, as quantidades são somadas em uma única linha." por: "Se o produto já estiver no rascunho, as quantidades são somadas em uma única linha, mantendo o preço unitário da primeira inclusão."

## Critérios de aceite

1. `./mvnw -B verify` termina com `BUILD SUCCESS`, incluindo o `ArchitectureTest`.
2. `grep -rn "import lombok\|import jakarta\|import org.springframework" src/main/java/com/github/svitorz/sells/domain` não retorna nada.
3. `grep -rn "LocalDateTime.now()\|LocalDate.now()" src/main/java/com/github/svitorz/sells/domain` não retorna nada.

## Commit

```
feat(domain): implement pure domain model with business rules
```
