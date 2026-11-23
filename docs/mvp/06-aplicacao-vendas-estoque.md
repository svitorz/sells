# Passo 06: Camada de aplicação, parte 2 (vendas e estoque)

## Objetivo

Implementar os casos de uso de venda (UC08 a UC15) e de estoque (UC17 e UC18), testados com os fakes do passo 05. Este é o núcleo do sistema: a finalização e o cancelamento precisam manter o saldo e as movimentações sempre consistentes (ADR-0005 e ADR-0007).

## Pré-requisitos

Passo 05 concluído. As convenções da camada (seção "Convenções da camada" do passo 05) continuam valendo.

## Fora do escopo

Persistência real, bloqueio real de linha e interface.

## Tarefas

### 6.1 Records de vendas (`application.sale`)

O `SaleSearch` já existe desde o passo 05. Crie:

```java
public record SaleItemView(long productId, String productName, String barcode, ProductUnit unit,
                           BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {}

public record SaleView(long id, LocalDateTime date, SaleStatus status, Long customerId, String customerName,
                       long userId, String userName, List<SaleItemView> items, BigDecimal subtotal,
                       LocalDateTime cancelledAt, String cancelledByName, String cancelReason) {}

public record SaleSummaryView(long id, LocalDateTime date, SaleStatus status, String customerName,
                              String userName, int itemCount, BigDecimal subtotal) {}

/** Returned when a draft line would exceed the current stock (UC09 warning). */
public record StockWarning(String productName, BigDecimal available, ProductUnit unit) {}

/** warning is null when the quantity fits the current stock. */
public record AddItemResult(SaleView sale, StockWarning warning) {}
```

### 6.2 `SaleService`

Construtor:

```java
public SaleService(SaleRepository sales, ProductRepository products, CustomerRepository customers,
                   UserRepository users, StockMovementRepository movements, SessionContext session,
                   TransactionRunner tx, Clock clock)
```

A constante `SEARCH_LIMIT = 500`.

#### Método privado `loadDraftForActor(long saleId, AuthenticatedUser actor)`

1. `sales.findById(saleId)`. Se não existir, lança `NotFoundException("Venda não encontrada.")`.
2. `sale.requireDraft()`.
3. Se `sale.getUserId() != actor.id()` e `!actor.isAdmin()`, lança `AccessDeniedException("Este rascunho pertence a outro usuário.")`.
4. Retorna a venda.

#### Métodos públicos

Todos exigem `session.require()`, exceto `cancel`, que exige `session.requireAdmin()`. Todos rodam dentro de `tx.inTransaction`.

| Método | Comportamento |
|---|---|
| `Optional<SaleView> findOpenDraft()` | `sales.findLatestDraftByUser(actor.id())` |
| `SaleView startDraft()` | `sales.save(Sale.startDraft(actor.id(), now))` (UC08) |
| `AddItemResult addItem(long saleId, long productId, BigDecimal quantity)` | Ver 6.3 |
| `AddItemResult addItemByBarcode(long saleId, String barcode, BigDecimal quantity)` | `products.findActiveByBarcode(barcode.trim())`. Se não existir (ou se o barcode estiver em branco), lança `NotFoundException("Nenhum produto encontrado com o código " + barcode.trim() + ".")`. Depois segue igual a `addItem`. |
| `SaleView changeItemQuantity(long saleId, long productId, BigDecimal quantity)` | `loadDraftForActor`, `sale.changeItemQuantity(...)`, salva (UC10) |
| `SaleView removeItem(long saleId, long productId)` | `loadDraftForActor`, `sale.removeItem(...)`, salva (UC10) |
| `SaleView assignCustomer(long saleId, Long customerId)` | `loadDraftForActor`. Se `customerId != null` e `customers.findActiveById` estiver vazio, lança `NotFoundException("Cliente não encontrado.")`. `sale.assignCustomer(...)`, salva (UC11) |
| `void deleteDraft(long saleId)` | `loadDraftForActor`, `sales.deleteById(saleId)` (UC12). Nenhuma movimentação é criada. |
| `SaleView complete(long saleId)` | Ver 6.4 |
| `SaleView cancel(long saleId, String reason)` | Ver 6.5 |
| `SaleView get(long saleId)` | `NotFoundException("Venda não encontrada.")` se não existir |
| `List<SaleSummaryView> search(SaleSearch criteria)` | Ver 6.6 |

### 6.3 `addItem` (UC09)

1. `sale = loadDraftForActor(saleId, actor)`.
2. `product = products.findActiveById(productId)`, ou `NotFoundException("Produto não encontrado.")`.
3. `item = sale.addItem(product, quantity, now)`.
4. `saved = sales.save(sale)`.
5. Se `item.getQuantity().compareTo(product.getStock()) > 0`, `warning = new StockWarning(product.getName(), product.getStock(), product.getUnit())`. Caso contrário, `warning = null`.
6. Retorna `new AddItemResult(toView(saved), warning)`.

O aviso **não** bloqueia a inclusão (ADR-0005, item 6).

### 6.4 `complete` (UC13) — siga esta ordem exatamente

A ordem importa: nenhuma alteração em memória pode acontecer antes de todas as verificações passarem.

1. `sale = loadDraftForActor(saleId, actor)`.
2. `ids` = os `productId` dos itens, em uma `List<Long>` ordenada de forma crescente.
3. `locked = products.findAllByIdsForUpdate(ids)`, transformado em `Map<Long, Product>` por id.
4. **Verificação**, para cada item, na ordem dos itens:
   - Se `product.isDeleted()`, lança `DomainException("O produto \"" + name + "\" foi excluído. Remova-o da venda para finalizar.")`.
   - Se `product.getStock().compareTo(item.getQuantity()) < 0`, lança `DomainException("Estoque insuficiente para \"" + name + "\". Disponível: " + Decimals.format(product.getStock()) + ".")`.
5. `sale.complete(now)`. Isso lança exceção se a venda não tiver itens.
6. Para cada item:
   - `product.applyStockChange(item.getQuantity().negate(), now)`.
   - `products.save(product)`.
   - Adiciona `StockMovement.sale(product.getId(), item.getQuantity(), sale.getId(), actor.id(), now)` a uma lista.
7. `saved = sales.save(sale)`.
8. `movements.saveAll(lista)`.
9. Retorna `toView(saved)`.

Tudo dentro de **uma** chamada `tx.inTransaction` (ADR-0005). O bloqueio de linha da etapa 3 evita que duas vendas simultâneas vendam a mesma unidade. No passo 07, isso é testado com o banco real.

### 6.5 `cancel` (UC14) — siga esta ordem exatamente

1. `actor = session.requireAdmin()`.
2. `sale = sales.findById(saleId)`, ou `NotFoundException("Venda não encontrada.")`.
3. `sale.cancel(actor.id(), reason, now)`. Isso valida o status `COMPLETED` e o motivo **antes** de qualquer alteração no estoque.
4. `locked = products.findAllByIdsForUpdate(ids ordenados)`.
5. Para cada item:
   - `product.applyStockChange(item.getQuantity(), now)`.
   - `products.save(product)`.
   - Adiciona `StockMovement.saleReturn(product.getId(), item.getQuantity(), sale.getId(), actor.id(), now)`.
6. `saved = sales.save(sale)`, depois `movements.saveAll(lista)`.
7. Retorna `toView(saved)`.

O estoque é devolvido mesmo que o produto tenha sido excluído depois da venda (ver a nota do passo 04, item 4.5).

### 6.6 `search` (UC15)

1. Se `criteria == null`, use `new SaleSearch(null, null, null, null, null)`.
2. Se `from` e `to` não forem nulos e `from.isAfter(to)`, lança `DomainException("A data inicial deve ser anterior ou igual à data final.")`.
3. `found = sales.search(criteria, SEARCH_LIMIT)`.
4. Busque os nomes em lote: **uma** chamada a `customers.findNamesByIds` com os customerIds não nulos e **uma** chamada a `users.findNamesByIds` com os userIds.
5. Monte cada `SaleSummaryView`. `itemCount` é o número de linhas (`sale.getItems().size()`). `customerName` é `null` quando não há cliente.

### 6.7 `toView(Sale)` (método privado)

- Produtos: **uma** chamada a `products.findAllByIds(productIds)`, que inclui excluídos, para obter nome e barcode.
- Cliente: `customers.findNamesByIds(Set.of(customerId))`, apenas se `customerId != null`.
- Usuários: **uma** chamada a `users.findNamesByIds` com `userId` e, se houver, `cancelledByUserId`.
- Os itens seguem a ordem de `sale.getItems()`. `total` é `item.total()`.

### 6.8 `StockService` (`application.stock`) — UC17, UC18

```java
public record StockMovementView(long id, StockMovementType type, BigDecimal quantity, LocalDateTime createdAt,
                                String userName, Long saleId, String notes) {}
```

Construtor: `StockService(ProductRepository products, StockMovementRepository movements, UserRepository users, SessionContext session, TransactionRunner tx, Clock clock)`. **Todos os métodos exigem `session.requireAdmin()`.**

`StockMovementView registerEntry(long productId, BigDecimal quantity, String notes)`:

1. `product` = o primeiro de `products.findAllByIdsForUpdate(List.of(productId))`. Se a lista estiver vazia ou o produto estiver excluído, lança `NotFoundException("Produto não encontrado.")`.
2. `qty = product.getUnit().quantity(quantity)`.
3. `movement = StockMovement.entry(productId, qty, actor.id(), notes, now)`.
4. `product.applyStockChange(qty, now)`, depois `products.save(product)`.
5. `saved = movements.saveAll(List.of(movement)).get(0)`. Retorna a view.

`StockMovementView registerAdjustment(long productId, BigDecimal delta, String notes)`: igual, mas com `qty = product.getUnit().delta(delta)` e `StockMovement.adjustment(...)`, que exige o motivo. O `StockMovement` é criado **antes** de `applyStockChange`, para que um ajuste sem motivo não altere o estoque. Se o ajuste deixar o estoque negativo, `applyStockChange` lança a mensagem de estoque insuficiente.

`List<StockMovementView> listMovements(long productId)`:

1. Se `products.findAllByIds(List.of(productId))` estiver vazio, lança `NotFoundException("Produto não encontrado.")`. Produtos excluídos são aceitos, porque o histórico continua consultável.
2. `movements.findByProductId(productId)` (ordem cronológica).
3. Os nomes de usuário vêm de **uma** chamada a `users.findNamesByIds`.

### 6.9 Ajuste no `ApplicationTestFixture`

Adicione `SaleService` e `StockService` ao fixture, com os mesmos fakes e o mesmo `MutableClock`. Adicione os métodos auxiliares:

```java
ProductView createProduct(String name, String barcode, String price, ProductUnit unit, String initialStock); // logs in as admin, restores the previous session afterwards
CustomerView createCustomer(String name);
```

## Testes obrigatórios

`SaleServiceTest` (`src/test/java/com/github/svitorz/sells/application/sale/`):

1. `findOpenDraft` fica vazio no começo; depois de `startDraft`, retorna o rascunho; com dois rascunhos, retorna o de maior id.
2. `addItem` cria a linha e atualiza o subtotal. `addItemByBarcode` funciona. Um barcode desconhecido lança `NotFoundException` com `Nenhum produto encontrado com o código 999.`
3. `addItem` com quantidade acima do estoque retorna um `warning` com o estoque disponível; com quantidade dentro do estoque, o `warning` é `null`.
4. O operador B não pode alterar o rascunho do operador A (`Este rascunho pertence a outro usuário.`); um admin pode.
5. `assignCustomer` com cliente inexistente lança `NotFoundException`; com `null`, remove o cliente.
6. `complete`: o estoque diminui, é criada uma movimentação `SALE` negativa por item com o `saleId`, o status vira `COMPLETED` e o subtotal não muda.
7. `complete` com estoque insuficiente lança exatamente `Estoque insuficiente para "Arroz". Disponível: 1.`; o estoque não muda, a venda continua `DRAFT` e nenhuma movimentação é criada.
8. `complete` com um produto excluído depois de entrar no rascunho é rejeitado, e nada muda.
9. `complete` de um rascunho vazio lança `A venda precisa ter pelo menos um item para ser finalizada.`
10. `deleteDraft` remove a venda; `deleteDraft` de uma venda concluída lança `Esta venda não está mais em rascunho e não pode ser alterada.`
11. `cancel` por operador lança `AccessDeniedException`. `cancel` por admin muda o status para `CANCELLED`, devolve o estoque e cria movimentações `RETURN`. `cancel` de um rascunho lança `Somente vendas concluídas podem ser canceladas.` sem alterar o estoque. `cancel` com motivo em branco é rejeitado.
12. `cancel` devolve o estoque mesmo que o produto tenha sido excluído depois da venda.
13. `search`: por padrão não inclui rascunhos; filtra por status, por período (os limites são inclusivos; use `MutableClock.set` para criar vendas em dias diferentes), por usuário e por cliente; `from` depois de `to` é rejeitado.
14. `get` preenche `productName`, `customerName`, `userName` e, depois do cancelamento, `cancelledByName` e `cancelReason`.

`StockServiceTest` (`src/test/java/com/github/svitorz/sells/application/stock/`):

1. `registerEntry` aumenta o estoque e cria uma movimentação `ENTRY`.
2. `registerEntry` com 0 é rejeitado; com produto excluído, lança `NotFoundException`.
3. `registerAdjustment` negativo diminui o estoque. Sem motivo, é rejeitado e o estoque não muda. Um ajuste que deixaria o estoque negativo é rejeitado com a mensagem de estoque insuficiente.
4. `registerEntry` de `1.5` em produto `UN` é rejeitado.
5. `listMovements` retorna em ordem cronológica, com o nome do usuário, e inclui a movimentação `SALE` de uma venda concluída.
6. Um operador recebe `AccessDeniedException` nos três métodos.

## Documentação a atualizar

`docs/use-cases.md`:

1. UC09, em "Regras", acrescente: "O aviso de estoque compara a quantidade total da linha com o saldo atual do produto."
2. UC10, UC11 e UC12, em "Regras", acrescente: "O operador só altera os próprios rascunhos. O administrador pode alterar qualquer rascunho."
3. UC13, em "Regras", acrescente: "Se um produto do rascunho foi excluído, a finalização é bloqueada até que o item seja removido."
4. UC14, em "Regras", acrescente: "O estoque é devolvido mesmo que o produto tenha sido excluído depois da venda."
5. UC15, em "Regras", acrescente: "Rascunhos não aparecem no histórico. Os filtros de período incluem as datas inicial e final. A consulta retorna no máximo 500 vendas, das mais recentes para as mais antigas."
6. UC17, em "Regras", acrescente: "Entradas e ajustes não são permitidos em produtos excluídos."

## Critérios de aceite

1. `./mvnw -B verify` termina com `BUILD SUCCESS`, incluindo o `ArchitectureTest`.
2. Cada um dos 14 casos de `SaleServiceTest` e dos 6 de `StockServiceTest` tem pelo menos um método de teste correspondente.

## Commit

```
feat(application): implement sale and stock use cases
```
