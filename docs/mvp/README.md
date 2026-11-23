# Plano do MVP

Este diretório contém o plano para levar o projeto do estado atual (schema, entidades e documentação) até um **executável real**: um aplicativo desktop que um pequeno comércio baixa, descompacta e usa, sem instalar Java, PostgreSQL ou Docker.

O plano foi escrito para ser executado **passo a passo por um agente de código** (ou por uma pessoa). Cada passo é um arquivo próprio, com escopo fechado, código de referência, testes obrigatórios e critérios de aceite verificáveis.

## 1. Diagnóstico do estado atual

Verificado em 2026-11-23, na branch `feat/implementar-usecases` (commit `009c4ba`).

| # | Divergência ou problema | Onde | Passo que resolve |
|---|---|---|---|
| D1 | O README manda rodar `docker compose up -d --build`, mas o `compose.yml` só sobe PostgreSQL e pgAdmin. O `Dockerfile` empacota um app desktop JavaFX numa imagem JRE sem display, então ele não consegue abrir a janela. Isso também contradiz as ADR-0003 e ADR-0012, segundo as quais a distribuição é nativa, com banco embutido. | `README.md`, `Dockerfile` | 01, 13 |
| D2 | O modo `embedded` é o padrão segundo a ADR-0003, mas não existe código que inicie o banco embutido. O `application.properties` aponta fixo para `localhost:5432`, e as variáveis `DB_*` do `.env` não são lidas pela aplicação. | `application.properties` | 01, 03 |
| D3 | 2 dos 14 testes (`SellsApplicationTests` e `DatabaseConnectionTest`) falham sem um PostgreSQL externo rodando. | `src/test` | 01 |
| D4 | `flyway-database-postgresql` está fixado em `10.11.0`, mas o Spring Boot 4.1 traz o `flyway-core` `12.4.0`. As duas versões são incompatíveis. | `pom.xml` | 01 |
| D5 | As anotações `jakarta.validation` (`@NotBlank`, `@Email` etc.) não têm implementação em runtime (o Hibernate Validator só está no classpath de teste). Na prática, nenhuma validação acontece. | `pom.xml`, `domain/*` | 01, 04 |
| D6 | `spring.batch.jdbc.initialize-schema` está configurado, mas o projeto não usa Spring Batch. O `spring-dotenv` e o `spring.config.import` fazem a mesma coisa duas vezes. | `application.properties`, `pom.xml` | 01 |
| D7 | No `compose.yml`, o `pgpass` aponta para o host `db_postgres`, mas o serviço se chama `sells-postgres`. Com o `DB_FORWARD_PORT` vazio do `.env.example`, o mapeamento de porta fica inválido. | `compose.yml`, `.env.example` | 01 |
| D8 | O "domínio" é composto de entidades JPA anêmicas com setters públicos. Por exemplo, `Product.setStock` contradiz o UC05 e a ADR-0005. `Sale` não tem getters, setters nem a coleção de itens, e `User.sales` fica `null` quando o objeto é criado pelo builder. Nenhuma regra de negócio está implementada e não existe camada de casos de uso. | `domain/*` | 02, 04, 05, 06, 07 |
| D9 | O `@SQLRestriction` em `Product` pode esconder produtos excluídos quando os itens de vendas antigas são carregados. Isso quebra o histórico que a ADR-0006 promete preservar. | `Product.java` | 07 |
| D10 | `SellsApplication` estende `javafx.application.Application` e é a classe `main`. Executar um jar comum assim falha com "JavaFX runtime components are missing". É preciso um `Launcher` separado para empacotar. | `SellsApplication.java` | 02 |
| D11 | O UC19 diz que `pg_dump` e `pg_restore` são fornecidos com a aplicação, mas o jar de binários do embedded-postgres traz apenas `initdb`, `pg_ctl` e `postgres` (verificado no `embedded-postgres-binaries-linux-amd64-16.15.0`). | `use-cases.md` | 12 |
| D12 | As ADR-0010, ADR-0011 e ADR-0012 citam um `CONTRIBUTING.md` que não existe. A ADR-0007 cita "conforme o DER", que foi substituído por `database.md`. | `docs/adr` | 13, 14 |
| D13 | `products.description` é `NOT NULL`, o que obriga a cadastrar descrição em todo produto. O UC05 não exige isso, e para um comércio pequeno é um atrito desnecessário. | `V1__CREATE_INITIAL_SCHEMA.sql` | 07 |
| D14 | Não há CI, embora as ADR-0003 e ADR-0012 dependam do GitHub Actions para validar o Windows. | — | 13 |
| D15 | **Risco de perda de dados.** A biblioteca `embedded-postgres` foi feita para testes e inicia o PostgreSQL com `-F` (fsync desligado) e `synchronous_commit=off`. Numa queda de energia, vendas já confirmadas podem ser perdidas e o banco pode corromper. Verificado no bytecode da versão 2.2.2. Também verifiquei, executando a biblioteca, que `-c fsync=on` sobrescreve o `-F`. | `embedded-postgres` | 03 |

## 2. Decisões tomadas neste plano

Estas decisões viram ADRs ou atualizações de ADRs nos passos indicados. Se alguma delas não for aceita, ajuste o passo correspondente **antes** de executá-lo.

1. **Arquitetura hexagonal (ports & adapters) em um único módulo Maven**, com as regras de dependência verificadas por testes ArchUnit (passo 02, ADR-0013). O domínio é Java puro, sem Spring, JPA, Lombok nem JavaFX. Trocar banco, ORM ou interface significa reescrever só o adaptador.
2. **Entidades JPA separadas do modelo de domínio**, com mapeamento explícito (passo 07). Isso custa mais classes, mas é o que permite trocar a persistência sem tocar nas regras.
3. **Sem `@SQLRestriction` nem `@SQLDelete`.** A exclusão lógica é um método do domínio, e os filtros `deleted_at IS NULL` ficam explícitos nas consultas do adaptador (passo 07, atualiza a ADR-0006).
4. **Datas de criação e alteração definidas pelo domínio**, com um `java.time.Clock` injetado, em vez da auditoria do Spring Data (passo 04).
5. **Backup lógico com `COPY` via JDBC**, em vez de `pg_dump` (passo 12, ADR-0014). Funciona com o banco ligado, não depende de binários extras e sobrevive a uma troca de versão maior do PostgreSQL.
6. **Distribuição inicial como pasta portátil** (`jpackage --type app-image`, compactada em `.zip` ou `.tar.gz`), sem instalador `.msi` (passo 13, atualiza a ADR-0012). Assim não dependemos do WiX no CI. O instalador fica para depois do MVP.
7. **Remoção do `Dockerfile`.** O `compose.yml` continua, mas só como banco de desenvolvimento para o modo `external` (passo 01).
8. **Interface JavaFX escrita em código, sem FXML.** As chamadas aos serviços são síncronas, exceto backup e restauração (passos 08 a 12).
9. **`products.description` passa a ser opcional** (passo 07).
10. **Durabilidade do banco embutido.** A aplicação religa `fsync` e `synchronous_commit`, que a biblioteca desliga por padrão (D15, passo 03).
11. **Senha do banco embutido (item 7 da ADR-0004) fica para depois do MVP.** No MVP, o PostgreSQL embutido aceita conexões apenas em `localhost`. A limitação fica documentada.

## 3. Arquitetura-alvo

```
com.github.svitorz.sells
├── Launcher                    main(); decide entre UI e --smoke-test
├── SellsApplication            @SpringBootApplication (raiz de composição)
├── SellsFxApplication          ciclo de vida JavaFX: banco → Spring → janela
├── SmokeTest                   verificação sem interface (passo 13)
├── domain                      Java puro: entidades, regras, exceções
│   ├── shared  user  product  customer  sale  stock
├── application                 casos de uso; depende só de domain
│   ├── port                    interfaces de saída (repositórios, hash, transação, backup)
│   ├── session  common  user  catalog  customer  sale  stock  backup
├── infrastructure              adaptadores; depende de application e domain
│   ├── config                  @Configuration que instancia os serviços
│   ├── database                ciclo de vida do PostgreSQL embutido (sem Spring)
│   ├── persistence.jpa         entidades JPA, Spring Data, adaptadores, mappers
│   ├── security  transaction  backup
└── ui                          JavaFX; depende de application (e só de enums do domain)
    ├── support  shell  auth  catalog  customer  user  sale  stock  backup
```

Regras de dependência (verificadas pelo `ArchitectureTest` a partir do passo 02):

| Pacote | Pode depender de |
|---|---|
| `domain..` | apenas `domain..` e `java..` |
| `application..` | apenas `application..`, `domain..` e `java..` |
| `infrastructure..` | qualquer pacote, exceto `ui..` |
| `ui..` | `ui..`, `application..`, `java..`, `javafx..`, `org.springframework..` (só para `@Component`), `org.slf4j..`; do `domain..`, **somente enums e `DomainException`** |
| raiz (`Launcher` etc.) | qualquer pacote (é a raiz de composição) |

## 4. Escopo do MVP

Entram no MVP os casos de uso UC01 a UC15 e UC17 a UC19 de `docs/use-cases.md`. O UC16 (estoque baixo) continua fora do MVP.

Entregável final: o arquivo `sells-0.1.0-windows-x64.zip`, e o equivalente `linux-x64.tar.gz`, gerados no GitHub Actions. Descompactado, o programa abre com um duplo clique, cria o banco em `~/.sells` e leva o usuário ao cadastro do primeiro administrador.

## 5. Ordem dos passos

| Passo | Arquivo | Resultado |
|---|---|---|
| 01 | [01-saneamento-build.md](01-saneamento-build.md) | Build e testes verdes sem Docker; dependências e configuração corrigidas |
| 02 | [02-arquitetura.md](02-arquitetura.md) | ADR-0013, pacotes, ArchUnit, `Launcher` separado |
| 03 | [03-banco-embutido.md](03-banco-embutido.md) | PostgreSQL embutido com lock de instância; app abre sem Docker |
| 04 | [04-dominio.md](04-dominio.md) | Modelo de domínio puro, com regras e testes unitários |
| 05 | [05-aplicacao-cadastros.md](05-aplicacao-cadastros.md) | Portas, sessão, usuários, categorias, produtos, clientes |
| 06 | [06-aplicacao-vendas-estoque.md](06-aplicacao-vendas-estoque.md) | Vendas (UC08 a UC15) e estoque (UC17, UC18) |
| 07 | [07-persistencia.md](07-persistencia.md) | Adaptadores JPA, transação, hash de senha, testes de integração |
| 08 | [08-ui-base.md](08-ui-base.md) | Janela, primeiro admin, login, menu por perfil |
| 09 | [09-ui-cadastros.md](09-ui-cadastros.md) | Telas de categorias, produtos, clientes e usuários |
| 10 | [10-ui-caixa.md](10-ui-caixa.md) | Tela de caixa (PDV) com código de barras e atalhos |
| 11 | [11-ui-historico-estoque.md](11-ui-historico-estoque.md) | Histórico, cancelamento e telas de estoque |
| 12 | [12-backup.md](12-backup.md) | Exportar e restaurar backup (UC19) |
| 13 | [13-empacotamento-ci.md](13-empacotamento-ci.md) | jpackage, smoke test e GitHub Actions (Linux e Windows) |
| 14 | [14-documentacao-release.md](14-documentacao-release.md) | Auditoria final da documentação e preparação da v0.1.0 |

Os passos são sequenciais: cada um depende de todos os anteriores.

## 6. Regras para quem executa (obrigatórias)

Leia estas regras antes de **cada** passo.

### 6.1 Processo

1. Execute **um passo por vez**, na ordem da tabela. Não comece o passo N+1 antes que todos os critérios de aceite do passo N estejam cumpridos.
2. Leia o arquivo do passo inteiro antes de alterar qualquer coisa.
3. Faça **somente** o que o passo pede. Se encontrar algo errado fora do escopo, anote no relatório final do passo, mas não corrija.
4. Se uma instrução for ambígua, contraditória ou impossível (por exemplo, uma API que não existe na versão da biblioteca), **pare e pergunte** à pessoa responsável. Não invente uma alternativa.
5. Ao terminar, rode `./mvnw -B verify` e confirme `BUILD SUCCESS`. Se um teste falhar, corrija o código. Não desative, apague nem marque como `@Disabled` um teste para fazer o build passar.
6. Faça commit ao final do passo, com a mensagem indicada no passo (Conventional Commits, em inglês). **Não faça push nem abra PR** sem pedido explícito.
7. Termine com um relatório curto: o que foi feito, os arquivos criados ou alterados, a saída resumida do `./mvnw -B verify` e qualquer desvio do plano, com o motivo.

### 6.2 Código

1. Java 21: records, `switch` com setas e text blocks são permitidos. Não use APIs de versões mais novas, mesmo que o JDK local seja mais novo.
2. Identificadores, comentários e mensagens de commit em **inglês**. Textos exibidos ao usuário, incluindo as mensagens de exceções de negócio, em **português** (ADR-0010).
3. Indentação de 4 espaços. Sem imports com `*`. Um tipo público por arquivo.
4. Injeção de dependência **somente por construtor**. Nunca use `@Autowired` em campo.
5. Lombok é permitido **somente** em `infrastructure.persistence.jpa.entity`. Em `domain`, `application` e `ui`, escreva o código explicitamente.
6. Valores monetários e quantidades usam sempre `BigDecimal`, nunca `double` ou `float`.
7. Nada de `System.out.println` no código de produção, exceto no `SmokeTest` (passo 13). Use SLF4J (`LoggerFactory.getLogger`).
8. Não altere a migration `V1__CREATE_INITIAL_SCHEMA.sql`, exceto quando um passo mandar (permitido antes da primeira release, pela ADR-0011).
9. Não adicione dependências além das que o passo indica.

### 6.3 Documentação

Todo passo tem uma seção **"Documentação a atualizar"**. Ela faz parte do passo: um passo não está concluído se o código e a documentação divergirem.

## 7. Como executar cada passo com um agente

Use **uma sessão nova por passo**, para que o contexto fique limpo, e revise o resultado antes de iniciar o próximo. Instrução sugerida para o agente:

```text
Leia docs/mvp/README.md inteiro, com atenção especial à seção 6 (regras obrigatórias).
Depois execute exatamente o passo descrito em docs/mvp/NN-<nome>.md.
Não faça nada fora do escopo do passo. Se algo estiver ambíguo ou não funcionar
como descrito, pare e me pergunte antes de improvisar.
Ao final, rode ./mvnw -B verify, faça o commit indicado no passo (sem push)
e me apresente o relatório pedido na regra 6.1.7.
```

Entre os passos, a pessoa responsável deve:

1. Ler o diff do commit (`git show --stat` e `git show`).
2. Executar o roteiro de teste manual, quando o passo tiver um (passos 03 e 08 a 12).
3. Só então liberar o passo seguinte.
