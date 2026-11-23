# Passo 02: Arquitetura em camadas e separação do launcher

## Objetivo

Criar a estrutura de pacotes da arquitetura hexagonal, registrar a decisão em uma ADR, garantir as regras de dependência com ArchUnit e separar o `main` da classe JavaFX. Resolve D10 e prepara D8.

## Pré-requisitos

Passo 01 concluído, com `./mvnw -B verify` verde.

## Fora do escopo

- Não crie classes de domínio novas (passo 04).
- Não reescreva as entidades JPA: neste passo elas só mudam de pacote e de nome.

## Tarefas

### 2.1 ADR-0013

Crie `docs/adr/0013-arquitetura-hexagonal.md` com exatamente este conteúdo:

```markdown
# ADR-0013: Arquitetura hexagonal em um único módulo

- **Status:** Aceita
- **Data:** 2026-11-23

## Contexto

O projeto deve ser fácil de manter por contribuidores diferentes e precisa permitir a troca de tecnologias (banco de dados, ORM, interface gráfica) sem reescrever as regras de negócio. Até aqui, as entidades JPA eram o modelo de domínio, com setters públicos e nenhuma regra implementada.

## Decisão

1. O código é dividido em quatro camadas, em pacotes de um único módulo Maven:
   - `domain`: entidades e regras de negócio, em Java puro.
   - `application`: casos de uso, portas de saída (interfaces) e objetos de visualização.
   - `infrastructure`: adaptadores que implementam as portas (JPA, PostgreSQL, hash de senha, transação, backup) e a configuração do Spring.
   - `ui`: interface JavaFX, que conversa apenas com a camada `application`.
2. `domain` depende apenas de `java.*`. `application` depende apenas de `domain` e `java.*`. `ui` não depende de `infrastructure` e, do `domain`, usa apenas enums e `DomainException`.
3. As entidades JPA ficam em `infrastructure.persistence.jpa.entity` e são convertidas de e para o domínio por mappers explícitos.
4. As classes da raiz do pacote (`Launcher`, `SellsApplication`, `SellsFxApplication`) são a raiz de composição e podem depender de todas as camadas.
5. As regras são verificadas automaticamente pelo teste `ArchitectureTest` (ArchUnit). Um build que viola as regras falha.

## Motivo

- Regras de negócio sem dependência de framework podem ser testadas sem banco e sem interface, e continuam válidas se a tecnologia mudar.
- Um único módulo mantém o build e o empacotamento simples. O ArchUnit dá a mesma garantia que módulos separados dariam para as regras de dependência.
- Separar as entidades JPA do domínio evita que anotações e limitações do ORM (construtor vazio, setters, carregamento lazy) contaminem as regras.

## Consequências

- Há mais classes: cada entidade persistida tem uma classe de domínio, uma entidade JPA e um mapper.
- Trocar o banco ou o ORM significa reescrever o pacote `infrastructure.persistence`, sem tocar em `domain`, `application` e `ui`.
- Trocar a interface significa reescrever o pacote `ui`.
- Lombok só é permitido nas entidades JPA.
```

### 2.2 Mover os enums para o domínio

Use `git mv` para preservar o histórico. Atualize o `package` de cada arquivo:

| De | Para |
|---|---|
| `domain/Role.java` | `domain/user/Role.java` (`package com.github.svitorz.sells.domain.user;`) |
| `domain/ProductUnit.java` | `domain/product/ProductUnit.java` |
| `domain/SaleStatus.java` | `domain/sale/SaleStatus.java` |
| `domain/StockMovementType.java` | `domain/stock/StockMovementType.java` |

O conteúdo dos enums não muda.

### 2.3 Mover e renomear as entidades JPA

Use `git mv` para cada arquivo. Destino: `src/main/java/com/github/svitorz/sells/infrastructure/persistence/jpa/entity/`, com `package com.github.svitorz.sells.infrastructure.persistence.jpa.entity;`.

| Classe atual (`domain`) | Nova classe |
|---|---|
| `User` | `UserEntity` |
| `Category` | `CategoryEntity` |
| `Product` | `ProductEntity` |
| `Customer` | `CustomerEntity` |
| `Address` | `AddressEmbeddable` |
| `Sale` | `SaleEntity` |
| `SaleItem` | `SaleItemEntity` |
| `StockMovement` | `StockMovementEntity` |
| `Auditable` | `AuditableEntity` |

Regras:

- Renomeie a classe e todas as referências a ela, incluindo os tipos genéricos (`List<Sale>` vira `List<SaleEntity>`) e o atributo `mappedBy`, que referencia o nome do **campo**, não da classe. Os nomes de campo não mudam.
- Os `@Table(name = ...)` continuam iguais, porque os nomes das tabelas não mudam.
- Atualize os imports dos enums movidos em 2.2.
- Mova `config/JpaConfig.java` para `infrastructure/config/JpaConfig.java` (`package com.github.svitorz.sells.infrastructure.config;`). Ela continua com `@EnableJpaAuditing` até o passo 07.
- Ao final, o diretório `src/main/java/com/github/svitorz/sells/domain/` deve conter apenas os quatro subpacotes com os enums.

### 2.4 Arquivos `package-info.java`

Crie estes arquivos, cada um com apenas o Javadoc e a declaração de pacote:

`src/main/java/com/github/svitorz/sells/domain/package-info.java`:
```java
/**
 * Business entities and rules. Plain Java only: no Spring, JPA, Lombok or JavaFX.
 * See docs/adr/0013-arquitetura-hexagonal.md.
 */
package com.github.svitorz.sells.domain;
```

`src/main/java/com/github/svitorz/sells/application/package-info.java`:
```java
/**
 * Use cases, output ports and view models. Depends only on the domain.
 * See docs/adr/0013-arquitetura-hexagonal.md.
 */
package com.github.svitorz.sells.application;
```

`src/main/java/com/github/svitorz/sells/infrastructure/package-info.java`:
```java
/**
 * Adapters that implement the application ports (database, persistence,
 * security, transactions, backup) and the Spring configuration.
 * See docs/adr/0013-arquitetura-hexagonal.md.
 */
package com.github.svitorz.sells.infrastructure;
```

`src/main/java/com/github/svitorz/sells/ui/package-info.java`:
```java
/**
 * JavaFX user interface. Talks only to the application layer.
 * See docs/adr/0013-arquitetura-hexagonal.md.
 */
package com.github.svitorz.sells.ui;
```

### 2.5 Separar o launcher

1. Reescreva `src/main/java/com/github/svitorz/sells/SellsApplication.java`:

```java
package com.github.svitorz.sells;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring configuration root. It scans every package below this one.
 * The process entry point is {@link Launcher}.
 */
@SpringBootApplication
public class SellsApplication {
}
```

2. Crie `src/main/java/com/github/svitorz/sells/SellsFxApplication.java`. Esta é uma versão **provisória**. O passo 03 adiciona o banco embutido e o passo 08 adiciona a janela real.

```java
package com.github.svitorz.sells;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX lifecycle: starts Spring in init(), shows the window in start()
 * and shuts everything down in stop().
 */
public class SellsFxApplication extends Application {

    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        context = new SpringApplicationBuilder(SellsApplication.class)
                .headless(false)
                .web(WebApplicationType.NONE)
                .run(getParameters().getRaw().toArray(String[]::new));
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("Sells");
        stage.setScene(new Scene(new StackPane(new Label("Sells")), 800, 500));
        stage.setOnCloseRequest(event -> Platform.exit());
        stage.show();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }
}
```

3. Crie `src/main/java/com/github/svitorz/sells/Launcher.java`:

```java
package com.github.svitorz.sells;

import javafx.application.Application;

/**
 * Process entry point. It must not extend javafx.application.Application,
 * otherwise the JVM refuses to start JavaFX from the classpath.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(SellsFxApplication.class, args);
    }
}
```

4. No `pom.xml`, no plugin `javafx-maven-plugin`, troque o `<mainClass>` para `com.github.svitorz.sells.Launcher`.

### 2.6 Teste de arquitetura

Crie `src/test/java/com/github/svitorz/sells/ArchitectureTest.java`:

```java
package com.github.svitorz.sells;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.github.svitorz.sells", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.github.svitorz.sells";

    @ArchTest
    static final ArchRule domainDependsOnlyOnJava = classes()
            .that().resideInAPackage(ROOT + ".domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(ROOT + ".domain..", "java..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDependsOnlyOnDomain = classes()
            .that().resideInAPackage(ROOT + ".application..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(ROOT + ".application..", ROOT + ".domain..", "java..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule infrastructureDoesNotDependOnUi = noClasses()
            .that().resideInAPackage(ROOT + ".infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".ui..", "javafx..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule uiDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage(ROOT + ".ui..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    ROOT + ".infrastructure..", "jakarta.persistence..", "org.hibernate..",
                    "org.springframework.data..", "org.springframework.jdbc..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule uiUsesOnlyEnumsAndDomainExceptionFromDomain = noClasses()
            .that().resideInAPackage(ROOT + ".ui..")
            .should().dependOnClassesThat(
                    resideInAPackage(ROOT + ".domain..")
                            .and(not(describe("are enums", JavaClass::isEnum)))
                            .and(not(name(ROOT + ".domain.shared.DomainException"))))
            .allowEmptyShould(true);
}
```

Se a combinação de predicados não compilar por causa da inferência de tipos genéricos, ajuste só os tipos (por exemplo, declarando uma variável `DescribedPredicate<JavaClass>`). A regra em si não pode mudar.

## Testes obrigatórios

- `ArchitectureTest`, com as 5 regras passando.
- Todos os testes do passo 01 continuam passando.

## Documentação a atualizar

- `docs/adr/0013-arquitetura-hexagonal.md` (criado em 2.1).
- `README.md`: adicione, abaixo do bloco "Desenvolvimento", a linha: `A arquitetura está descrita em docs/adr/0013-arquitetura-hexagonal.md.`

## Critérios de aceite

1. `./mvnw -B verify` termina com `BUILD SUCCESS`.
2. `find src/main/java/com/github/svitorz/sells/domain -name '*.java' | sort` lista exatamente: `domain/package-info.java`, `domain/product/ProductUnit.java`, `domain/sale/SaleStatus.java`, `domain/stock/StockMovementType.java` e `domain/user/Role.java`.
3. `grep -rn "extends Application" src/main/java` encontra apenas `SellsFxApplication`.
4. `git log --follow` em `infrastructure/persistence/jpa/entity/ProductEntity.java` mostra o histórico anterior, o que comprova que o `git mv` foi usado.

## Commit

```
refactor: introduce hexagonal package layout and separate launcher
```
