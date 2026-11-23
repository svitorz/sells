# Passo 11: Histórico de vendas, cancelamento e estoque

## Objetivo

Implementar as telas de histórico (UC15), cancelamento de venda concluída (UC14) e estoque (UC17 e UC18).

## Pré-requisitos

Passo 10 concluído.

## Tarefas

### 11.1 `ui.sale.SalesHistoryView` (UC15)

Construtor: `SalesHistoryView(SaleService saleService, UserService userService, CustomerService customerService, SessionContext session)`.

**Filtros**, numa linha:

| Rótulo | Componente | Padrão |
|---|---|---|
| `De` | `DatePicker` | hoje menos 30 dias |
| `Até` | `DatePicker` | hoje |
| `Situação` | `ComboBox<StatusOption>`, em que `private record StatusOption(String label, SaleStatus status)` sobrescreve `toString()` para retornar `label`. Itens: `("Todas", null)`, `("Concluída", COMPLETED)` e `("Cancelada", CANCELLED)`. Não coloque `null` diretamente nos itens do combo. | `Todas` |
| `Operador` | `ComboBox<UserOption>` com o sentinela `new UserOption(-1, "Todos")` primeiro, seguido de `userService.listUserOptions()` | `Todos` |
| `Cliente` | Botão com o texto `Cliente: todos` ou `Cliente: <nome>`. Ele abre o `CustomerPickerDialog`: `Sem cliente` volta a `todos`, e uma escolha filtra por aquele cliente. | `todos` |
| — | Botão `Buscar` | — |

> "Hoje" vem de `LocalDate.now()`. Isso é permitido na `ui`, porque a regra de não usar `now()` vale apenas para o domínio.

A busca chama `saleService.search(new SaleSearch(de, ate, status, customerId, userId))` e roda ao abrir a tela e a cada clique em `Buscar`. Use `null` para os filtros vazios.

**Tabela** `TableView<SaleSummaryView>`, com as colunas `Nº` (direita, 70), `Data` (`Formats.dateTime`, 140), `Situação` (`Concluída` ou `Cancelada`, 100), `Cliente` (vazio quando não houver, 220), `Operador` (160), `Itens` (direita, 60) e `Total` (direita, `Formats.money`, 120). Placeholder: `Nenhuma venda encontrada no período.`

**Rodapé**: o rótulo `<n> venda(s) concluída(s) · Total: <soma>`, que soma apenas as vendas `COMPLETED` exibidas. Se a lista tiver 500 linhas, acrescente ` · Mostrando as 500 mais recentes. Refine os filtros.`

**Detalhes**: o botão `Detalhes` (desabilitado sem seleção), o duplo clique ou Enter na tabela abrem `SaleDetailsDialog.open(owner, saleService, session, id)`. Se o diálogo retornar `true` (houve cancelamento), refaça a busca.

### 11.2 `ui.sale.SaleDetailsDialog` (UC14, UC15)

```java
/** Returns true when the sale was cancelled from this dialog. */
public static boolean open(Window owner, SaleService saleService, SessionContext session, long saleId)
```

- Carrega `saleService.get(saleId)`. Se houver erro, mostra o erro e retorna `false`.
- `Dialog<Boolean>` com o título `Venda nº <id>`, tamanho preferido de 760 x 520. Conteúdo, de cima para baixo:
  1. Uma `GridPane` com `Data`, `Situação`, `Operador` e `Cliente` (`não informado` quando não houver).
  2. Uma tabela de itens com `Produto`, `Qtd`, `Un`, `Preço unit.` e `Total`.
  3. O rótulo `Total: <subtotal>` em negrito.
  4. Somente se a venda estiver `CANCELLED`: um bloco com `Cancelada em <dateTime> por <cancelledByName>` e `Motivo: <cancelReason>`.
- Botões: `Fechar` (`CANCEL_CLOSE`) sempre. `Cancelar venda` (`ButtonData.OTHER`) **somente** se `session.require().isAdmin()` e a venda estiver `COMPLETED`.
- `Cancelar venda` usa um filtro de evento (como o `FormDialog`, para o diálogo não fechar antes da hora):
  1. `Dialogs.textInput(owner, "Cancelar venda", "Motivo do cancelamento", true)`. Se cancelado, consome o evento e não faz nada.
  2. `Dialogs.confirm(owner, "Cancelar a venda nº <id>? O estoque dos itens será devolvido.")`. Se a resposta for "Não", consome o evento.
  3. `UiActions.run(..., () -> saleService.cancel(id, motivo))`. Em caso de erro, consome o evento (o diálogo continua aberto). Em caso de sucesso, o resultado do diálogo é `true`.

### 11.3 `ui.stock.StockView` (UC17, UC18)

Construtor: `StockView(ProductService productService, StockService stockService)`.

Layout: um `SplitPane` horizontal (40% e 60%).

**Esquerda, produtos:**

- Busca (`TextField` e o botão `Buscar`), usando `productService.search(termo, null)`.
- Tabela com `Nome` (220), `Un` (50) e `Estoque` (direita, 100).
- Ao selecionar um produto, o lado direito carrega as movimentações dele.

**Direita, movimentações do produto selecionado:**

- Título: `<nome do produto> · estoque atual: <quantidade> <un>`, ou `Selecione um produto` sem seleção.
- Botões `Registrar entrada` e `Registrar ajuste`, desabilitados sem seleção.
- Tabela `TableView<StockMovementView>`, com as colunas `Data` (140), `Tipo` (110), `Quantidade` (direita, `Formats.signedQuantity(quantity, unit)`, 110), `Usuário` (150), `Venda` (`nº <id>` ou vazio, 80) e `Observações` (250).
- Tipos exibidos: `ENTRY` como `Entrada`, `SALE` como `Venda`, `RETURN` como `Devolução` e `ADJUSTMENT` como `Ajuste`.

**Registrar entrada**: um `FormDialog<StockMovementView>` com o título `Entrada de estoque`, os campos `Quantidade` (`TextField`) e `Observações` (`TextArea`, opcional, com o prompt `Ex.: nota fiscal 123, fornecedor X`). Ao salvar, chama `stockService.registerEntry(productId, Formats.parseDecimal(qtd), obs)`.

**Registrar ajuste**: igual, com o título `Ajuste de estoque`, os campos `Diferença` (com o prompt `Use negativo para retirar, ex.: -2`) e `Motivo` (obrigatório). Chama `stockService.registerAdjustment(...)`.

Depois de salvar, recarregue a lista de produtos (mantendo a seleção pelo id) e as movimentações.

### 11.4 Ligar ao menu

`ViewFactory`:

```java
public Parent salesHistory() { return new SalesHistoryView(saleService, userService, customerService, session).getRoot(); }
public Parent stock()        { return new StockView(productService, stockService).getRoot(); }
```

No `ShellView`, `Vendas` usa `salesHistory()` e `Estoque` usa `stock()`. Também acrescente a `Formats`:

```java
public static String saleStatusName(SaleStatus status) // DRAFT "Rascunho", COMPLETED "Concluída", CANCELLED "Cancelada"
public static String movementTypeName(StockMovementType type) // as listed in 11.3
```

e um caso no `FormatsTest` para cada valor desses dois enums.

## Roteiro de teste manual

1. Faça 3 vendas no caixa (uma delas com cliente).
2. **Vendas:** as 3 aparecem, da mais recente para a mais antiga, e o rodapé soma os totais. Filtre por cliente, por operador e por situação.
3. Abra os detalhes de uma venda: os itens e o total conferem.
4. Como admin, cancele uma venda: sem motivo, nada acontece; com motivo, a situação vira `Cancelada` e os detalhes mostram quem cancelou e por quê.
5. **Estoque:** o produto da venda cancelada mostra uma movimentação `Venda` (negativa) e uma `Devolução` (positiva), e o estoque voltou ao valor anterior.
6. Registre uma entrada de 10 com observação: o estoque aumenta 10.
7. Tente um ajuste sem motivo: aparece o erro e o formulário continua aberto.
8. Tente um ajuste de `-1000`: aparece "Estoque insuficiente para ...".
9. Entre como operador: o menu não tem Estoque; nos detalhes de uma venda, não aparece o botão "Cancelar venda".

## Documentação a atualizar

Nenhuma.

## Critérios de aceite

1. `./mvnw -B verify` termina com `BUILD SUCCESS`, incluindo os novos casos do `FormatsTest`.
2. O roteiro manual passa (ou fica registrado no relatório para validação da pessoa responsável).

## Commit

```
feat(ui): add sales history, sale cancellation and stock screens
```
