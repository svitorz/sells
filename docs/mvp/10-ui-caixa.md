# Passo 10: Tela de caixa (PDV)

## Objetivo

Implementar a tela usada o dia inteiro pelo operador: leitura de código de barras, busca de produto por nome, quantidade (inclusive em quilos), cliente opcional, total em destaque, atalhos de teclado e finalização. Ela cobre os casos de uso UC08 a UC13.

## Pré-requisitos

Passo 09 concluído.

## Princípios desta tela

1. **O teclado é o caminho principal.** O leitor de código de barras funciona como um teclado: ele digita o código e envia Enter. O foco precisa voltar sozinho ao campo de código depois de cada ação.
2. **Avisos não interrompem.** O aviso de estoque (UC09) aparece num rótulo laranja, nunca num diálogo modal, para não atrapalhar a leitura do próximo item.
3. **O rascunho fica no banco.** Se o programa fechar, a venda aberta é retomada na próxima vez que o mesmo operador abrir o caixa (`findOpenDraft`).

## Tarefas

### 10.1 `ui.catalog.ProductPickerDialog`

```java
public static Optional<ProductView> open(Window owner, ProductService productService, String initialTerm)
```

- Um `Dialog<ProductView>` modal com o título `Buscar produto` e tamanho preferido de 800 x 500.
- Conteúdo: um `TextField` de busca (com `initialTerm`), o botão `Buscar` e uma `TableView<ProductView>` com as colunas `Nome` (300), `Código` (150), `Preço` (direita, 110) e `Estoque` (direita, `quantity + " " + unitLabel`, 120).
- A busca (`productService.search(termo, null)`) roda ao abrir e a cada Enter no campo ou clique em `Buscar`. Depois da busca, se houver resultados, selecione a primeira linha.
- Seta para baixo no campo de busca move o foco para a tabela.
- Enter na tabela ou duplo clique numa linha escolhe o produto: `dialog.setResult(item)`, que fecha o diálogo.
- Botões: `Selecionar` (`OK_DONE`, retorna a linha selecionada, ou `null` sem seleção) e `Cancelar`.

### 10.2 `ui.customer.CustomerPickerDialog`

```java
public record CustomerChoice(CustomerView customer) {}   // customer == null means "no customer"

public static Optional<CustomerChoice> open(Window owner, CustomerService customerService)
```

- Um `Dialog<CustomerChoice>` modal com o título `Cliente da venda`.
- Conteúdo: busca (`TextField` e o botão `Buscar`, como no 10.1, usando `customerService.search`), uma tabela com `Nome`, `Telefone` e `CPF/CNPJ`, e o botão comum (não `ButtonType`) `Novo cliente…`, que abre `CustomerFormDialog.open(...)`. Se um cliente for criado, chame `dialog.setResult(new CustomerChoice(criado))`.
- `ButtonType`s:
  - `Selecionar` (`OK_DONE`): retorna `new CustomerChoice(selecionado)`, ou `null` sem seleção.
  - `Sem cliente` (`ButtonData.OTHER`): retorna `new CustomerChoice(null)`.
  - `Cancelar` (`CANCEL_CLOSE`): retorna `null`.
- Duplo clique ou Enter na tabela escolhe o cliente.

### 10.3 `ui.sale.PosView`

Construtor: `PosView(SaleService saleService, ProductService productService, CustomerService customerService)`.

Estado: um único campo `private SaleView current;` (`null` quando não há rascunho aberto).

#### Layout (`BorderPane` com a classe `page`)

```
┌──────────────────────────────────────────────────────────────────────────┐
│ [saleLabel: "Nova venda" | "Venda nº 12 · rascunho"]   [customerLabel] [Cliente (F4)] │
│ Qtd [quantityField] Código ou nome [codeField..........] [Adicionar] [Buscar produto (F2)] │
│ [warningLabel (laranja)]                                                  │
├──────────────────────────────────────────────────────────────────────────┤
│ Produto | Código | Qtd | Un | Preço unit. | Total                        │
│ ...                                                                      │
├──────────────────────────────────────────────────────────────────────────┤
│ [Alterar quantidade (F6)] [Remover item (Del)]        Total              │
│                                                      R$ 0,00 (pos-total) │
│ [Excluir rascunho (F8)]                     [Finalizar venda (F10)]      │
└──────────────────────────────────────────────────────────────────────────┘
```

- `quantityField`: texto inicial `1`, `prefColumnCount = 6`.
- `codeField`: cresce horizontalmente (`HBox.setHgrow(codeField, Priority.ALWAYS)`) e tem o prompt `Leia o código de barras ou digite o nome`.
- `customerLabel`: `Cliente: não informado`, ou `Cliente: <nome>`.
- Colunas: `Produto` (300), `Código` (150), `Qtd` (direita, `Formats.quantity`, 90), `Un` (50), `Preço unit.` (direita, 110), `Total` (direita, 110). Placeholder: `Nenhum item. Leia um código de barras para começar.`
- `Alterar quantidade` e `Remover item` ficam desabilitados sem seleção. `Finalizar venda` e `Excluir rascunho` ficam desabilitados quando `current == null`. `Finalizar venda` também fica desabilitado quando `current.items()` está vazio.

#### Método `show(SaleView sale)`

Atualiza `current`, a tabela, o `saleLabel`, o `customerLabel`, o total (`Formats.money(sale.subtotal())`, ou `R$ 0,00` sem venda) e o estado dos botões. Toda ação que recebe um `SaleView` do serviço termina chamando `show(...)`.

#### Método `long ensureDraftId()`

Se `current == null`, faz `current = saleService.startDraft()`. Retorna `current.id()`. É chamado **dentro** das ações que modificam a venda, para que o rascunho só seja criado quando o primeiro item for adicionado ou o cliente for escolhido (UC08).

#### Ações

| Gatilho | Comportamento |
|---|---|
| Abrir a tela | `UiActions.call(root, saleService::findOpenDraft)`, depois `show(resultado ou null)` e `Platform.runLater(codeField::requestFocus)` |
| Enter em `quantityField` | Move o foco para `codeField` |
| Enter em `codeField` ou clique em `Adicionar` | `addFromCodeField()` (ver abaixo) |
| `F2` ou `Buscar produto` | `searchProduct()` (ver abaixo) |
| `F4` ou `Cliente` | `CustomerPickerDialog.open(...)`; se houver escolha, `saleService.assignCustomer(ensureDraftId(), choice.customer() == null ? null : choice.customer().id())` e `show` |
| `F6` ou `Alterar quantidade` | Com item selecionado: `Dialogs.textInput(owner, "Alterar quantidade", "Nova quantidade de \"<produto>\"", false)`; se houver texto, `saleService.changeItemQuantity(current.id(), productId, Formats.parseDecimal(texto))` e `show` |
| `Delete` com foco na tabela, ou `Remover item` | Com item selecionado: `Dialogs.confirm(owner, "Remover \"<produto>\" da venda?")`; se confirmado, `saleService.removeItem(...)` e `show` |
| `F8` ou `Excluir rascunho` | Com `current != null`: `Dialogs.confirm(owner, "Excluir o rascunho atual? Todos os itens serão removidos.")`; se confirmado, `saleService.deleteDraft(current.id())`, `show(null)` e limpa o aviso |
| `F10` ou `Finalizar venda` | Ver "Finalizar" abaixo |

Depois de **qualquer** ação acima, devolva o foco ao `codeField`.

**Adicionar item**: implemente exatamente com estes três métodos.

```java
/** Enter in codeField or the "Adicionar" button. */
private void addFromCodeField() {
    String code = codeField.getText().trim();
    if (code.isEmpty()) {
        return;
    }
    BigDecimal quantity;
    Optional<ProductView> byBarcode;
    try {
        quantity = Formats.parseDecimal(quantityField.getText());
        byBarcode = productService.findByBarcode(code);
    } catch (RuntimeException e) {
        Dialogs.error(UiActions.windowOf(root), ErrorMessages.of(e));
        codeField.requestFocus();
        return;
    }
    Optional<ProductView> product = byBarcode.isPresent()
            ? byBarcode
            : ProductPickerDialog.open(UiActions.windowOf(root), productService, code);
    product.ifPresent(found -> addProduct(found.id(), quantity));
    codeField.requestFocus();
}

/** F2 or the "Buscar produto" button. */
private void searchProduct() {
    Optional<BigDecimal> quantity = UiActions.call(root, () -> Formats.parseDecimal(quantityField.getText()));
    if (quantity.isEmpty()) {
        return;
    }
    ProductPickerDialog.open(UiActions.windowOf(root), productService, codeField.getText().trim())
            .ifPresent(found -> addProduct(found.id(), quantity.get()));
    codeField.requestFocus();
}

private void addProduct(long productId, BigDecimal quantity) {
    UiActions.call(root, () -> saleService.addItem(ensureDraftId(), productId, quantity))
            .ifPresent(result -> {
                show(result.sale());
                StockWarning warning = result.warning();
                warningLabel.setText(warning == null ? "" : "Atenção: o estoque disponível de \""
                        + warning.productName() + "\" é " + Formats.quantity(warning.available(), warning.unit())
                        + " " + Formats.unitLabel(warning.unit()) + ".");
                codeField.clear();
                quantityField.setText("1");
                result.sale().items().stream()
                        .filter(item -> item.productId() == productId)
                        .findFirst()
                        .ifPresent(item -> {
                            table.getSelectionModel().select(item);
                            table.scrollTo(item);
                        });
            });
}
```

**Finalizar**:

1. Se `current == null` ou não houver itens, não faça nada.
2. `Dialogs.confirm(owner, "Finalizar a venda no valor de " + Formats.money(current.subtotal()) + "?")`.
3. Se confirmado: `UiActions.call(root, () -> saleService.complete(current.id()))`.
4. Em caso de sucesso: `Dialogs.info(owner, "Venda nº " + id + " finalizada.\nTotal: " + Formats.money(subtotal))`, depois `show(null)`, limpa o aviso e foco em `codeField`.
5. Em caso de erro (por exemplo, estoque insuficiente), o diálogo de erro mostra a mensagem do serviço e o rascunho continua aberto, para que o operador corrija.

#### Atalhos de teclado

Registre um único filtro na raiz:

```java
root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
    boolean handled = switch (event.getCode()) {
        case F2 -> { searchProduct(); yield true; }
        case F4 -> { chooseCustomer(); yield true; }
        case F6 -> { changeQuantity(); yield true; }
        case F8 -> { deleteDraft(); yield true; }
        case F10 -> { finish(); yield true; }
        default -> false;
    };
    if (handled) {
        event.consume();
    }
});
```

A tecla `Delete` é tratada **apenas** em `table.setOnKeyPressed(...)`, para não interferir na edição dos campos de texto.

### 10.4 Ligar ao menu

`ViewFactory.pos()` retorna `new PosView(saleService, productService, customerService).getRoot()`. No `ShellView`, o item `Caixa` passa a usar esse método.

## Testes obrigatórios

Não há testes automatizados de tela. A lógica de venda já está coberta pelos testes dos passos 06 e 07.

## Roteiro de teste manual

Prepare o cenário do passo 09 (`Arroz 5kg` com estoque 10 e o código `7891000100103`). Crie também `Banana` (KG, R$ 6,99, estoque 3).

1. Abra o Caixa: aparece "Nova venda", com o total R$ 0,00 e o foco no campo de código.
2. Digite `7891000100103` e pressione Enter: o arroz entra com a quantidade 1. O campo é limpo e o foco volta.
3. Repita: continua uma única linha, agora com a quantidade 2.
4. Digite `0,750` em Qtd, Enter, digite `banana` e Enter: abre a busca; escolha Banana com Enter. Entra 0,750 kg, e o total é R$ 51,80 + R$ 5,24 = R$ 57,04.
5. Digite `20` em Qtd e leia o código do arroz: aparece o aviso laranja de estoque, e a linha fica com 22.
6. F10 e confirme: aparece o erro "Estoque insuficiente para "Arroz 5kg". Disponível: 10." e o rascunho continua.
7. Selecione o arroz, F6, quantidade `2`: o aviso não aparece mais na próxima inclusão.
8. F4, escolha um cliente: o rótulo mostra o nome. F4, `Sem cliente`: volta a "não informado".
9. Feche o programa sem finalizar. Abra de novo e entre com o mesmo usuário: o rascunho aparece no Caixa.
10. F10 e confirme: aparece "Venda nº N finalizada.", a tela volta a "Nova venda" e, em Produtos, o arroz tem estoque 8.
11. Adicione um item e pressione F8: o rascunho é excluído.
12. Com uma quantidade `1,5` para o arroz (UN): aparece "Produtos vendidos por unidade aceitam apenas quantidades inteiras."

## Documentação a atualizar

`docs/use-cases.md`:

1. UC08, em "Fluxo principal", acrescente: "Na interface, o rascunho é criado automaticamente quando o primeiro item é adicionado ou quando o cliente é escolhido. Ao abrir o caixa, o rascunho mais recente do operador é retomado."
2. UC09, em "Fluxo principal", acrescente: "Se o texto digitado não corresponder a um código de barras, o sistema abre a busca de produtos por nome."

## Critérios de aceite

1. `./mvnw -B verify` termina com `BUILD SUCCESS`.
2. O roteiro manual passa (ou fica registrado no relatório para validação da pessoa responsável).

## Commit

```
feat(ui): add point of sale screen with barcode input and shortcuts
```
