# Passo 09: Telas de cadastro (categorias, produtos, clientes e usuários)

## Objetivo

Implementar as telas dos casos de uso UC03 a UC07. A tela de categorias é dada como **exemplo completo**, e as demais seguem o mesmo padrão.

## Pré-requisitos

Passo 08 concluído. As regras da camada `ui` (passo 08) continuam valendo.

## Tarefas

### 9.1 Utilitário de tabelas (`ui.support.Tables`)

```java
package com.github.svitorz.sells.ui.support;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.util.function.Consumer;
import java.util.function.Function;

public final class Tables {

    private Tables() {
    }

    public static <T> TableColumn<T, String> column(String title, Function<T, String> value, double prefWidth) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(prefWidth);
        column.setSortable(false);
        return column;
    }

    public static <T> TableColumn<T, String> rightColumn(String title, Function<T, String> value, double prefWidth) {
        TableColumn<T, String> column = column(title, value, prefWidth);
        column.setStyle("-fx-alignment: CENTER-RIGHT;");
        return column;
    }

    public static <T> void onDoubleClick(TableView<T> table, Consumer<T> action) {
        table.setRowFactory(tableView -> {
            TableRow<T> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    action.accept(row.getItem());
                }
            });
            return row;
        });
    }

    public static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
```

Acrescente a `Formats`:

```java
public static String roleName(Role role) {
    return role == Role.ADMIN ? "Administrador" : "Operador";
}
```

### 9.2 Exemplo completo: `ui.catalog.CategoriesView` (UC04)

Implemente exatamente assim. As outras telas deste passo seguem esta estrutura: título, barra de ações, tabela que ocupa o espaço restante, `reload()` e formulário via `FormDialog`.

```java
package com.github.svitorz.sells.ui.catalog;

import com.github.svitorz.sells.application.catalog.CategoryCommand;
import com.github.svitorz.sells.application.catalog.CategoryService;
import com.github.svitorz.sells.application.catalog.CategoryView;
import com.github.svitorz.sells.ui.support.Dialogs;
import com.github.svitorz.sells.ui.support.FormDialog;
import com.github.svitorz.sells.ui.support.Tables;
import com.github.svitorz.sells.ui.support.UiActions;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class CategoriesView {

    private final CategoryService categoryService;
    private final VBox root = new VBox();
    private final TableView<CategoryView> table = new TableView<>();

    public CategoriesView(CategoryService categoryService) {
        this.categoryService = categoryService;

        Label title = new Label("Categorias");
        title.getStyleClass().add("page-title");

        Button add = new Button("Nova");
        Button edit = new Button("Editar");
        Button delete = new Button("Excluir");
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        add.setOnAction(event -> openForm(null));
        edit.setOnAction(event -> openForm(table.getSelectionModel().getSelectedItem()));
        delete.setOnAction(event -> deleteSelected());

        table.getColumns().add(Tables.column("Nome", CategoryView::name, 250));
        table.getColumns().add(Tables.column("Descrição", category -> Tables.orEmpty(category.description()), 450));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Nenhuma categoria cadastrada."));
        Tables.onDoubleClick(table, this::openForm);
        VBox.setVgrow(table, Priority.ALWAYS);

        root.getStyleClass().add("page");
        root.getChildren().addAll(title, new HBox(8, add, edit, delete), table);
        reload();
    }

    public Parent getRoot() {
        return root;
    }

    private void reload() {
        UiActions.call(root, categoryService::list).ifPresent(table.getItems()::setAll);
    }

    private void openForm(CategoryView existing) {
        TextField name = new TextField(existing == null ? "" : existing.name());
        TextArea description = new TextArea(existing == null ? "" : Tables.orEmpty(existing.description()));
        description.setPrefRowCount(3);
        new FormDialog<CategoryView>(UiActions.windowOf(root), existing == null ? "Nova categoria" : "Editar categoria")
                .field("Nome", name)
                .field("Descrição", description)
                .show(() -> {
                    CategoryCommand command = new CategoryCommand(name.getText(), description.getText());
                    return existing == null
                            ? categoryService.create(command)
                            : categoryService.update(existing.id(), command);
                })
                .ifPresent(saved -> reload());
    }

    private void deleteSelected() {
        CategoryView selected = table.getSelectionModel().getSelectedItem();
        UiActions.call(root, () -> categoryService.countProducts(selected.id())).ifPresent(count -> {
            String message = count == 0
                    ? "Excluir a categoria \"" + selected.name() + "\"?"
                    : "Excluir a categoria \"" + selected.name() + "\"? " + count
                      + " produto(s) perderão o vínculo com ela.";
            if (Dialogs.confirm(UiActions.windowOf(root), message)
                    && UiActions.run(root, () -> categoryService.delete(selected.id()))) {
                reload();
            }
        });
    }
}
```

### 9.3 `ui.catalog.ProductsView` e `ui.catalog.ProductFormDialog` (UC05, UC06)

**`ProductsView(ProductService productService, CategoryService categoryService)`**

- Título: `Produtos`.
- Barra de filtros: `TextField` de busca (com o prompt `Buscar por nome ou código de barras`, e Enter dispara a busca), `ComboBox<CategoryView>` de categoria e o botão `Buscar`.
  - O primeiro item do combo é o sentinela `new CategoryView(-1, "Todas as categorias", null)`, selecionado por padrão. Quando ele está selecionado, `categoryId = null`.
  - Trocar a categoria também dispara a busca.
  - O combo mostra `name()` (use `setConverter` com um `StringConverter`).
- Barra de ações: `Novo`, `Editar` e `Excluir`. `Editar` e `Excluir` ficam desabilitados sem seleção.
- Colunas: `Nome` (250), `Código` (150), `Preço` (direita, `Formats.money`, 110), `Un` (`Formats.unitLabel`, 50), `Estoque` (direita, `Formats.quantity(stock, unit)`, 100) e `Categorias` (nomes separados por `, `, 250).
- Placeholder da tabela: `Nenhum produto encontrado.`
- Duplo clique abre a edição.
- `reload()` chama `productService.search(termo, categoryId)`. Ele roda ao abrir a tela.
- Excluir: confirmação `Excluir o produto "<nome>"? Ele continuará aparecendo nas vendas antigas.` e então `productService.delete(id)`.

**`ProductFormDialog`**: uma classe com o método estático

```java
public static Optional<ProductView> open(Window owner, ProductService productService,
                                         CategoryService categoryService, ProductView existing)
```

Campos do `FormDialog`, nesta ordem:

| Rótulo | Componente | Observação |
|---|---|---|
| `Nome` | `TextField` | |
| `Código de barras` | `TextField` | Opcional |
| `Preço` | `TextField` | Lido com `Formats.parseDecimal`. Na edição, preenchido com `Formats.editable(price)` |
| `Unidade` | `ComboBox<ProductUnit>` | Itens `UN` e `KG`, exibidos com `Formats.unitName`. Padrão: `UN` |
| `Descrição` | `TextArea` (3 linhas) | Opcional |
| `Estoque inicial` | `TextField` | **Somente na criação.** Padrão `0`. Lido com `Formats.parseOptionalDecimal` |
| `Categorias` | `ListView<CategoryView>` com caixas de seleção | Altura de 150 px. Ver o código abaixo |

Caixas de seleção para as categorias:

```java
List<CategoryView> all = categoryService.list();
Set<Long> initiallySelected = existing == null ? Set.of()
        : existing.categories().stream().map(CategoryView::id).collect(Collectors.toSet());
Map<Long, BooleanProperty> checked = new HashMap<>();
for (CategoryView category : all) {
    checked.put(category.id(), new SimpleBooleanProperty(initiallySelected.contains(category.id())));
}
ListView<CategoryView> list = new ListView<>(FXCollections.observableArrayList(all));
list.setCellFactory(CheckBoxListCell.forListView(category -> checked.get(category.id()),
        new StringConverter<>() {
            @Override public String toString(CategoryView category) { return category == null ? "" : category.name(); }
            @Override public CategoryView fromString(String text) { return null; }
        }));
list.setPrefHeight(150);
// when saving:
Set<Long> categoryIds = checked.entrySet().stream()
        .filter(entry -> entry.getValue().get()).map(Map.Entry::getKey).collect(Collectors.toSet());
```

Salvar: na criação, `productService.create(new CreateProductCommand(...))`; na edição, `productService.update(id, new UpdateProductCommand(...))`. Erros de conversão de número (`IllegalArgumentException`) aparecem no `FormDialog` e mantêm o formulário aberto, porque a conversão é feita **dentro** do `Supplier` passado a `show`.

### 9.4 `ui.customer.CustomersView` e `ui.customer.CustomerFormDialog` (UC07)

**`CustomersView(CustomerService customerService)`**

- Título: `Clientes`.
- Busca: `TextField` com o prompt `Buscar por nome, telefone ou CPF/CNPJ` e o botão `Buscar` (Enter também busca).
- Ações: `Novo`, `Editar` e `Excluir`.
- Colunas: `Nome` (250), `Telefone` (130), `CPF/CNPJ` (150), `E-mail` (220) e `Cidade` (texto `cidade - UF`, ou só a cidade, ou vazio; 180).
- Placeholder: `Nenhum cliente encontrado.`
- Excluir: confirmação `Excluir o cliente "<nome>"? Ele continuará aparecendo nas vendas antigas.`

**`CustomerFormDialog`**:

```java
public static Optional<CustomerView> open(Window owner, CustomerService customerService, CustomerView existing)
```

Campos: `Nome`, `Telefone`, `E-mail`, `Data de nascimento` (`DatePicker`), `CPF/CNPJ`, `Rua`, `Número`, `Complemento`, `Bairro`, `Cidade`, `UF` e `CEP`. Na edição, preencha com os valores atuais, usando `""` no lugar de `null`. Salvar monta um `AddressData` com os 7 campos de endereço e um `CustomerCommand`.

Esse diálogo será reutilizado pelo caixa no passo 10. Por isso ele é público e estático.

### 9.5 `ui.user.UsersView` e `ui.user.UserFormDialog` (UC03)

**`UsersView(UserService userService)`**

- Título: `Usuários`.
- Ações: `Novo`, `Editar`, `Trocar senha` e um botão de situação, cujo texto é `Desativar` quando o usuário selecionado está ativo e `Reativar` quando está inativo (atualize o texto num listener da seleção). Todos, exceto `Novo`, ficam desabilitados sem seleção.
- Colunas: `Nome` (220), `Usuário` (150), `E-mail` (220), `Perfil` (`Formats.roleName`, 130) e `Situação` (`Ativo` ou `Inativo`, 90).
- `reload()` chama `userService.listUsers()`.
- Desativar: confirmação `Desativar o usuário "<nome>"? Ele não poderá mais entrar no sistema.` e então `deactivateUser`. Reativar: sem confirmação, `activateUser`.
- Trocar senha: um `FormDialog<Boolean>` com `Nova senha` e `Confirmar senha` (`PasswordField`). No `Supplier`: se as senhas forem diferentes, lance `new IllegalArgumentException("As senhas não conferem.")`; caso contrário, chame `userService.changePassword(id, senha)` e retorne `true`. Ao final, `Dialogs.info(..., "Senha alterada.")`.

**`UserFormDialog`**:

```java
public static Optional<UserView> open(Window owner, UserService userService, UserView existing)
```

Campos: `Nome`, `E-mail`, `Usuário`, `Perfil` (`ComboBox<Role>` exibido com `Formats.roleName`, padrão `USER`) e `Data de nascimento` (`DatePicker`). **Somente na criação**, também `Senha` e `Confirmar senha`, com a mesma verificação de igualdade da troca de senha.

### 9.6 Ligar as telas ao menu

Na `ViewFactory`, adicione:

```java
public Parent categories() { return new CategoriesView(categoryService).getRoot(); }
public Parent products()   { return new ProductsView(productService, categoryService).getRoot(); }
public Parent customers()  { return new CustomersView(customerService).getRoot(); }
public Parent users()      { return new UsersView(userService).getRoot(); }
```

No `ShellView`, troque os placeholders de `Clientes`, `Produtos`, `Categorias` e `Usuários` por esses métodos.

## Testes obrigatórios

Não há testes automatizados de tela no MVP. O `ArchitectureTest` e os testes existentes devem continuar passando.

## Roteiro de teste manual

1. **Categorias:** crie `Mercearia` e `Bebidas`, edite a descrição de uma delas e exclua `Bebidas` (a confirmação mostra 0 produtos).
2. **Produtos:**
   - Crie `Arroz 5kg` (UN, R$ 25,90, código `7891000100103`, estoque inicial 10, categoria Mercearia).
   - Crie `Queijo minas` (KG, R$ 49,90, estoque inicial `2,5`).
   - Tente criar outro produto com o código `7891000100103`: aparece a mensagem de código duplicado e o formulário continua aberto.
   - Tente o preço `abc`: aparece "Valor inválido: abc".
   - Busque `arroz` (minúsculo) e filtre pela categoria Mercearia.
   - Exclua `Queijo minas`: ele some da lista.
3. **Categorias de novo:** ao excluir `Mercearia`, a confirmação mostra "1 produto(s) perderão o vínculo com ela". Cancele.
4. **Clientes:** crie um cliente com o CPF `123.456.789-01` e a UF `sp`; ao editar, o CPF aparece só com dígitos e a UF como `SP`. Tente criar outro cliente com o mesmo CPF: aparece a mensagem de duplicidade.
5. **Usuários:** crie o operador `caixa1`. Faça logout e entre como `caixa1`: o menu mostra apenas Caixa, Vendas e Clientes. Volte como admin, desative `caixa1` e confirme que o login dele falha. Tente desativar o próprio admin: aparece a mensagem de erro.

## Documentação a atualizar

Nenhuma.

## Critérios de aceite

1. `./mvnw -B verify` termina com `BUILD SUCCESS`.
2. O roteiro manual passa (ou fica registrado no relatório para validação da pessoa responsável, se não houver display).

## Commit

```
feat(ui): add category, product, customer and user screens
```
