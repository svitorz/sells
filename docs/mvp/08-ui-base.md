# Passo 08: Base da interface (janela, primeiro administrador, login e menu)

## Objetivo

Substituir a janela provisória pela estrutura real da interface: o cadastro do primeiro administrador (UC02), o login (UC01), o menu lateral por perfil e o logout. Também cria os utilitários de interface que os passos 09 a 12 vão reutilizar.

## Pré-requisitos

Passo 07 concluído.

## Regras da camada `ui`

1. A interface é escrita **em código Java**, sem FXML.
2. A `ui` usa apenas os serviços e records de `application`, os enums do `domain` e `DomainException`. O `ArchitectureTest` garante isso.
3. Cada tela é uma classe `final` com construtor que recebe serviços e callbacks e um método `public Parent getRoot()`. A árvore de componentes é criada no construtor da tela.
4. As únicas classes da `ui` com anotação do Spring são `MainWindow` e `ViewFactory` (`@Component`). Os construtores dessas duas classes **não** podem criar componentes JavaFX, porque o contexto do Spring também é criado nos testes e no smoke test, onde o toolkit JavaFX não está iniciado.
5. Chamadas aos serviços são feitas **diretamente na thread do JavaFX**, sempre por meio de `UiActions.run(...)`, que trata os erros. A única exceção é o backup (passo 12), que usa `FxAsync`.
6. Todo texto visível está em português.
7. Datas são exibidas como `dd/MM/yyyy`, datas com hora como `dd/MM/yyyy HH:mm` e valores como moeda brasileira (`R$ 1.234,56`), sempre por meio de `Formats`.

## Tarefas

### 8.1 Utilitários (`ui.support`)

`Formats.java`:

```java
package com.github.svitorz.sells.ui.support;

import com.github.svitorz.sells.domain.product.ProductUnit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Formats {

    public static final Locale PT_BR = Locale.of("pt", "BR");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Formats() {
    }

    public static String money(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return NumberFormat.getCurrencyInstance(PT_BR).format(value);
    }

    /** UN: "3". KG: "0,350". */
    public static String quantity(BigDecimal value, ProductUnit unit) {
        if (value == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat(unit == ProductUnit.KG ? "#,##0.000" : "#,##0",
                DecimalFormatSymbols.getInstance(PT_BR));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value);
    }

    /** Signed quantity for stock movements: "+5", "-0,250". */
    public static String signedQuantity(BigDecimal value, ProductUnit unit) {
        String text = quantity(value.abs(), unit);
        return (value.signum() < 0 ? "-" : "+") + text;
    }

    public static String unitLabel(ProductUnit unit) {
        return unit == ProductUnit.KG ? "kg" : "un";
    }

    public static String unitName(ProductUnit unit) {
        return unit == ProductUnit.KG ? "Quilo (KG)" : "Unidade (UN)";
    }

    public static String date(LocalDate value) {
        return value == null ? "" : DATE.format(value);
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    /**
     * Parses "1.234,56", "1234,56", "0,350", "0.350" and "R$ 10,00".
     * With a comma, dots are thousand separators. Without a comma, a dot is the decimal separator.
     */
    public static BigDecimal parseDecimal(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Informe um valor.");
        }
        String cleaned = text.replace("R$", "").replace(" ", "").replace(" ", "").trim();
        if (cleaned.contains(",")) {
            cleaned = cleaned.replace(".", "").replace(',', '.');
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inválido: " + text.trim());
        }
    }

    /** Like parseDecimal, but returns null for blank text. */
    public static BigDecimal parseOptionalDecimal(String text) {
        return text == null || text.isBlank() ? null : parseDecimal(text);
    }

    /** Editable text for a decimal field: 10.50 becomes "10,50". */
    public static String editable(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
```

`Dialogs.java`: métodos estáticos, todos com `Alert` ou `Dialog` modal (`initModality(Modality.APPLICATION_MODAL)`) e dono definido quando houver janela (`initOwner(owner)`; aceite `Window owner` como primeiro parâmetro, que pode ser `null`).

| Método | Comportamento |
|---|---|
| `void error(Window owner, String message)` | `Alert` do tipo `ERROR`, cabeçalho `Não foi possível concluir`, conteúdo `message` |
| `void info(Window owner, String message)` | `Alert` do tipo `INFORMATION`, sem cabeçalho |
| `void warning(Window owner, String message)` | `Alert` do tipo `WARNING`, sem cabeçalho |
| `boolean confirm(Window owner, String message)` | `Alert` do tipo `CONFIRMATION` com os botões `Sim` e `Não` (`ButtonType` com esses textos). Retorna `true` apenas se `Sim` for escolhido. |
| `Optional<String> textInput(Window owner, String title, String label, boolean multiline)` | `Dialog<String>` com `TextField` (ou `TextArea` com 4 linhas, se `multiline`) e os botões `OK` e `Cancelar`. Retorna o texto digitado, ou vazio se cancelado. |

`ErrorMessages.java`:

```java
public static String of(Throwable error)
```

Retorna `error.getMessage()` quando `error` for instância de `DomainException`, `AccessDeniedException`, `AuthenticationException`, `NotFoundException`, `ConcurrentUpdateException` ou `IllegalArgumentException`. Para qualquer outro tipo, registra `log.error("Unexpected error", error)` e retorna `"Ocorreu um erro inesperado. Detalhes foram gravados no arquivo de log (~/.sells/logs/sells.log)."`.

`UiActions.java`:

```java
public final class UiActions {

    private UiActions() {
    }

    /** Runs the action and shows any error in a dialog. Returns true on success. */
    public static boolean run(Node source, Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            Dialogs.error(windowOf(source), ErrorMessages.of(e));
            return false;
        }
    }

    /** Runs the supplier and shows any error in a dialog. Returns empty on error. */
    public static <T> Optional<T> call(Node source, Supplier<T> action) {
        try {
            return Optional.ofNullable(action.get());
        } catch (RuntimeException e) {
            Dialogs.error(windowOf(source), ErrorMessages.of(e));
            return Optional.empty();
        }
    }

    public static Window windowOf(Node node) {
        return node == null || node.getScene() == null ? null : node.getScene().getWindow();
    }
}
```

`FxAsync.java`, usado apenas no passo 12:

```java
public final class FxAsync {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sells-background");
        thread.setDaemon(true);
        return thread;
    });

    private FxAsync() {
    }

    /** Runs work in the background, then onSuccess or onError on the JavaFX thread. */
    public static <T> void run(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        EXECUTOR.submit(() -> {
            try {
                T result = work.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable error) {
                Platform.runLater(() -> onError.accept(error));
            }
        });
    }
}
```

`FormDialog.java`: o padrão de formulário que **todos** os diálogos de cadastro (passo 09) vão usar. O diálogo só fecha quando o salvamento dá certo. Em caso de erro, a mensagem aparece e o formulário continua aberto.

```java
package com.github.svitorz.sells.ui.support;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Modal form. The save action runs when the user presses "Salvar"; if it throws,
 * the error is shown and the dialog stays open.
 */
public final class FormDialog<T> {

    private final Dialog<T> dialog = new Dialog<>();
    private final GridPane grid = new GridPane();
    private int row;
    private T result;

    public FormDialog(Window owner, String title) {
        dialog.setTitle(title);
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        grid.setHgap(10);
        grid.setVgap(8);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Salvar", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
    }

    public FormDialog<T> field(String label, Node input) {
        grid.addRow(row++, new Label(label), input);
        return this;
    }

    public Optional<T> show(Supplier<T> save) {
        ButtonType saveType = dialog.getDialogPane().getButtonTypes().get(0);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                result = save.get();
            } catch (RuntimeException e) {
                Dialogs.error(dialog.getDialogPane().getScene().getWindow(), ErrorMessages.of(e));
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == saveType ? result : null);
        return dialog.showAndWait();
    }
}
```
### 8.2 Estilo

Crie `src/main/resources/ui/sells.css`:

```css
.root { -fx-font-size: 14px; }
.menu-button { -fx-pref-width: 180px; -fx-alignment: center-left; -fx-padding: 10 14 10 14; }
.menu-button:selected { -fx-font-weight: bold; }
.sidebar { -fx-background-color: derive(-fx-base, -8%); -fx-padding: 12; -fx-spacing: 6; }
.topbar { -fx-padding: 8 12 8 12; -fx-spacing: 12; -fx-alignment: center-left; -fx-border-color: transparent transparent derive(-fx-base, -20%) transparent; }
.page { -fx-padding: 16; -fx-spacing: 12; }
.page-title { -fx-font-size: 20px; -fx-font-weight: bold; }
.pos-total { -fx-font-size: 36px; -fx-font-weight: bold; }
.pos-warning { -fx-text-fill: #b35c00; -fx-font-weight: bold; }
.form-title { -fx-font-size: 18px; -fx-font-weight: bold; }
```

### 8.3 Fábrica de telas e janela principal (`ui.shell`)

`ViewFactory` (`@Component`): recebe no construtor **todos** os serviços (`UserService`, `CategoryService`, `ProductService`, `CustomerService`, `SaleService`, `StockService`) e o `SessionContext`, e guarda as referências. Ela tem um método por tela, que retorna uma tela nova a cada chamada. Neste passo, implemente:

```java
public FirstAdminView firstAdmin(Runnable onDone)
public LoginView login(Runnable onSuccess)
public ShellView shell(Runnable onLogout)
public Parent placeholder(String title)   // VBox com um Label "Em construção: <title>"; usado até os passos 09-12
```

`MainWindow` (`@Component`):

```java
public MainWindow(UserService userService, ViewFactory views)

public void show(Stage stage)   // creates the Scene (1280x800), loads /ui/sells.css, title "Sells", min 1024x700, calls showEntry(), stage.show()
public void showEntry()          // userService.needsFirstAdmin() ? firstAdmin(this::showShell) : login(this::showShell)
public void showShell()          // scene.setRoot(views.shell(this::logout).getRoot())
public void logout()             // userService.logout(); showEntry()
```

O `Stage` só é recebido em `show`. O construtor apenas guarda as dependências.

Em `SellsFxApplication.start`, troque a criação da janela provisória por:

```java
context.getBean(MainWindow.class).show(stage);
stage.setOnCloseRequest(event -> Platform.exit());
```

### 8.4 Primeiro administrador (`ui.auth.FirstAdminView`) — UC02

Layout: um `VBox` centralizado (largura máxima de 420 px), com:

1. O título `Bem-vindo ao Sells` (classe `form-title`).
2. O texto `Cadastre o administrador do sistema. Ele poderá criar os demais usuários.`
3. Os campos `Nome`, `E-mail`, `Usuário`, `Senha` (`PasswordField`) e `Confirmar senha` (`PasswordField`).
4. O botão `Criar administrador` (botão padrão, acionado com Enter).

Ao clicar:

1. Se as senhas forem diferentes, `Dialogs.error(..., "As senhas não conferem.")` e pare.
2. `UiActions.run(root, () -> userService.createFirstAdmin(new NewUserCommand(nome, email, usuario, senha, Role.ADMIN, null)))`.
3. Em caso de sucesso, chame `onDone.run()`. Como o serviço já inicia a sessão, a próxima tela é o menu.

### 8.5 Login (`ui.auth.LoginView`) — UC01

Layout como em 8.4, com o título `Sells`, os campos `Usuário` e `Senha` e o botão padrão `Entrar`. Ao clicar, chame `UiActions.run(root, () -> userService.login(usuario, senha))`. Em caso de sucesso, `onSuccess.run()`. Em caso de erro, limpe o campo de senha e devolva o foco a ele. O campo `Usuário` recebe o foco ao abrir a tela (use `Platform.runLater(usernameField::requestFocus)`).

### 8.6 Menu (`ui.shell.ShellView`)

Estrutura: um `BorderPane`.

- **Topo** (classe `topbar`): o rótulo `Sells` em negrito, um espaçador (`Region` com `HBox.setHgrow(..., Priority.ALWAYS)`), o rótulo `<nome> · <perfil>` (com perfil `Administrador` para `ADMIN` e `Operador` para `USER`) e o botão `Sair`, que chama `onLogout.run()`.
- **Esquerda** (classe `sidebar`): `ToggleButton`s com a classe `menu-button`, num mesmo `ToggleGroup`. Impeça que o botão selecionado seja desmarcado ao clicar de novo (listener no `selectedToggleProperty` que reverte para o anterior quando o novo valor for `null`).
- **Centro:** a tela selecionada.

Itens do menu, nesta ordem. A coluna "Perfil" indica quem vê o item.

| Item | Perfil | Tela (neste passo) | Tela definitiva |
|---|---|---|---|
| Caixa | todos | `placeholder("Caixa")` | passo 10 |
| Vendas | todos | `placeholder("Vendas")` | passo 11 |
| Clientes | todos | `placeholder("Clientes")` | passo 09 |
| Produtos | admin | `placeholder("Produtos")` | passo 09 |
| Categorias | admin | `placeholder("Categorias")` | passo 09 |
| Estoque | admin | `placeholder("Estoque")` | passo 11 |
| Usuários | admin | `placeholder("Usuários")` | passo 09 |
| Backup | admin | `placeholder("Backup")` | passo 12 |

O perfil vem de `session.require()`. Ao abrir o menu, `Caixa` fica selecionado. Cada clique cria a tela **de novo** (chamando o método da `ViewFactory`), para que os dados estejam sempre atualizados.

Para facilitar os próximos passos, o `ShellView` recebe a `ViewFactory` e uma lista de entradas. Implemente um record privado `MenuEntry(String label, boolean adminOnly, Supplier<Parent> view)` e monte a lista no construtor.

## Testes obrigatórios

`src/test/java/com/github/svitorz/sells/ui/support/FormatsTest.java` (JUnit puro, sem toolkit JavaFX):

| Entrada | Esperado |
|---|---|
| `money(new BigDecimal("1234.5"))` | contém `R$` e termina com `1.234,50` |
| `quantity(new BigDecimal("3.000"), UN)` | `3` |
| `quantity(new BigDecimal("0.35"), KG)` | `0,350` |
| `signedQuantity(new BigDecimal("-0.250"), KG)` | `-0,250` |
| `parseDecimal("1.234,56")` | `1234.56` |
| `parseDecimal("0,350")` | `0.350` |
| `parseDecimal("0.350")` | `0.350` |
| `parseDecimal("R$ 10,00")` | `10.00` |
| `parseDecimal("abc")` | `IllegalArgumentException` com `Valor inválido: abc` |
| `parseDecimal(" ")` | `IllegalArgumentException` com `Informe um valor.` |
| `editable(new BigDecimal("10.50"))` | `10,5` |
| `dateTime(LocalDateTime.of(2026, 1, 5, 9, 7))` | `05/01/2026 09:07` |

> Para comparar `BigDecimal`, use `compareTo(...) == 0` ou compare `toPlainString()`.

O teste `SellsApplicationTests.contextLoads` continua passando, o que confirma que `MainWindow` e `ViewFactory` não criam componentes JavaFX no construtor.

## Roteiro de teste manual

Use um diretório de dados novo, para não misturar com dados existentes:

1. `rm -rf /tmp/sells-teste && SELLS_DATA_DIR=/tmp/sells-teste ./mvnw javafx:run` abre a tela "Bem-vindo ao Sells".
2. Com senhas diferentes, aparece "As senhas não conferem.".
3. Com uma senha de 5 caracteres, aparece "A senha deve ter pelo menos 6 caracteres.".
4. Com dados válidos, a tela muda para o menu, com 8 itens e o nome do usuário no topo.
5. `Sair` leva ao login. Uma senha errada mostra "Usuário ou senha inválidos." e limpa a senha.
6. Com o login correto, o menu volta.
7. Feche e abra o programa: ele abre no login, não no cadastro inicial.

## Documentação a atualizar

Nenhuma. A interface é descrita no README final (passo 13).

## Critérios de aceite

1. `./mvnw -B verify` termina com `BUILD SUCCESS`, incluindo o `ArchitectureTest` e o `FormatsTest`.
2. O roteiro manual passa. Se o ambiente não tiver display, registre no relatório que o roteiro ficou para a pessoa responsável.

## Commit

```
feat(ui): add main window, first admin setup, login and role-based menu
```
