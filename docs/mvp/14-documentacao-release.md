# Passo 14: Auditoria da documentação e preparação da v0.1.0

## Objetivo

Deixar a documentação coerente com o código, escrever o README definitivo e o `CONTRIBUTING.md`, e preparar a primeira versão (v0.1.0). Resolve D1 e D12 e encerra o MVP.

## Pré-requisitos

Passo 13 concluído, de preferência com o CI verde no GitHub.

## Fora do escopo

Criar a tag, fazer push, publicar a release no GitHub e alterar a versão depois da release. Tudo isso é decisão da pessoa responsável (ver 14.7).

## Tarefas

### 14.1 README definitivo

Substitua o `README.md` inteiro por este conteúdo:

````markdown
# Sells

Sistema de vendas desktop para pequenos comércios: caixa com leitor de código de barras, cadastro de produtos, clientes e usuários, controle de estoque e histórico de vendas.

O Sells é **software livre** (GPLv3): você pode usar, estudar, modificar e redistribuir, para qualquer finalidade.

## Funcionalidades

- Caixa (PDV) com leitor de código de barras, busca por nome, venda por unidade ou por quilo e atalhos de teclado.
- Cliente opcional na venda, com CPF ou CNPJ.
- Produtos com categorias, código de barras e estoque.
- Estoque com histórico de movimentações: entradas, ajustes, vendas e devoluções.
- Histórico de vendas com filtros e cancelamento pelo administrador, que devolve o estoque.
- Usuários com perfil de administrador ou operador de caixa.
- Backup e restauração em um único arquivo.

O Sells **não** emite documentos fiscais (NF-e, NFC-e ou SAT).

## Instalação

Não é preciso instalar Java nem banco de dados: tudo vem junto.

1. Baixe o arquivo da última versão em [Releases](https://github.com/svitorz/sells/releases):
   - Windows 10 ou 11 (64 bits): `sells-<versão>-windows-x64.zip`
   - Linux (64 bits): `sells-<versão>-linux-x64.tar.gz`
2. Descompacte em qualquer pasta.
3. Abra `Sells/Sells.exe` (Windows) ou `Sells/bin/Sells` (Linux).
4. Na primeira vez, cadastre o administrador.

Os dados ficam na pasta `.sells`, dentro da pasta do usuário (`C:\Users\<usuário>\.sells` no Windows). Faça backups pelo menu **Backup** e guarde o arquivo fora do computador.

## Atalhos do caixa

| Tecla | Ação |
|---|---|
| Enter | Adiciona o código lido ou busca pelo nome digitado |
| F2 | Buscar produto |
| F4 | Escolher cliente |
| F6 | Alterar a quantidade do item selecionado |
| Del | Remover o item selecionado |
| F8 | Excluir o rascunho |
| F10 | Finalizar a venda |

## Limitações conhecidas

- Não há versão para macOS nem para processadores ARM.
- O banco embutido aceita conexões apenas do próprio computador, mas ainda não exige senha de outros usuários do mesmo computador (ADR-0004).
- Um backup só pode ser restaurado na mesma versão do banco em que foi gerado (ADR-0014).

## Documentação

- [Domínio e glossário](docs/DOMAIN.md)
- [Casos de uso](docs/use-cases.md)
- [Modelo de dados](docs/database.md)
- [Decisões de arquitetura (ADRs)](docs/adr/)
- [Como contribuir](CONTRIBUTING.md)
- [Próximos passos](docs/roadmap.md)

## Licença

[GNU General Public License v3.0](LICENSE). O PostgreSQL distribuído junto usa a [PostgreSQL License](packaging/licenses/POSTGRESQL-LICENSE.txt).
````

### 14.2 `CONTRIBUTING.md`

Crie o arquivo na raiz com estas seções, nesta ordem. Os trechos de código são obrigatórios. O texto de cada seção pode ser escrito livremente, desde que cubra todos os itens listados.

1. **Requisitos:** JDK 21 ou mais novo. Docker é opcional, e só serve para o modo `external`.
2. **Rodar os testes:** `./mvnw -B verify`. O PostgreSQL de teste é embutido. Os testes não podem rodar como `root`.
3. **Abrir a aplicação:**
   ```bash
   ./mvnw javafx:run
   ```
   Para usar um diretório de dados separado do seu uso pessoal, use a variável de ambiente (o `javafx:run` executa a aplicação num processo separado, que herda as variáveis de ambiente):
   ```bash
   SELLS_DATA_DIR=/tmp/sells-dev ./mvnw javafx:run
   ```
4. **Modo external (PostgreSQL no Docker):**
   ```bash
   cp .env.example .env
   docker compose up -d
   SELLS_DB_MODE=external \
   SELLS_DB_URL=jdbc:postgresql://localhost:5432/sells \
   SELLS_DB_USERNAME=postgres \
   SELLS_DB_PASSWORD=postgres \
   ./mvnw javafx:run
   ```
   O pgAdmin sobe com `docker compose --profile tools up -d`, em `http://localhost:8080`.
5. **Tabela de configuração:** copie a tabela "Configuração" do passo 03 deste plano.
6. **Arquitetura:** um resumo da ADR-0013, a tabela de regras de dependência (copie a do `docs/mvp/README.md`, seção 3) e o aviso de que o `ArchitectureTest` faz o build falhar se as regras forem violadas.
7. **Onde colocar cada coisa:** uma regra de negócio vai para o `domain`; um novo caso de uso vai para um serviço em `application`; consultas novas viram métodos na porta e no adaptador; telas vão para a `ui`.
8. **Migrations:** um resumo da ADR-0011. Antes da primeira release, a `V1` pode ser reescrita, e quem já a aplicou deve apagar `~/.sells/pgdata` (ou o `pgdata` do diretório usado). Depois da v0.1.0, toda mudança vai numa nova migration (`V2__...`). Toda tabela nova precisa entrar na lista do backup (`PostgresCopyBackupGateway.TABLES`).
9. **Idioma e estilo:** um resumo da ADR-0010 e das regras 6.2 do `docs/mvp/README.md`.
10. **Commits:** Conventional Commits em inglês (`feat:`, `fix:`, `docs:`, `build:`, `refactor:`, `test:`).
11. **Gerar o pacote localmente:**
    ```bash
    ./mvnw -B package -DskipTests
    packaging/build-app-image.sh
    target/dist/Sells/bin/Sells --smoke-test
    ```
    No Windows, rode o script pelo Git Bash.

### 14.3 Rastreabilidade dos casos de uso

Em `docs/use-cases.md`, na tabela "Resumo", acrescente a coluna **Implementação**, com o serviço e a tela de cada caso de uso:

| ID | Implementação |
|---|---|
| UC01 | `UserService.login` · `LoginView` |
| UC02 | `UserService.createFirstAdmin` · `FirstAdminView` |
| UC03 | `UserService` · `UsersView` |
| UC04 | `CategoryService` · `CategoriesView` |
| UC05 | `ProductService` · `ProductsView` |
| UC06 | `ProductService.search`, `findByBarcode` · `ProductPickerDialog`, `PosView` |
| UC07 | `CustomerService` · `CustomersView` |
| UC08 a UC13 | `SaleService` · `PosView` |
| UC14 | `SaleService.cancel` · `SaleDetailsDialog` |
| UC15 | `SaleService.search`, `get` · `SalesHistoryView` |
| UC16 | — (pós-MVP) |
| UC17, UC18 | `StockService` · `StockView` |
| UC19 | `BackupService` · `BackupView` |

### 14.4 Auditoria

Rode cada comando abaixo na raiz do repositório e corrija toda ocorrência que **não** estiver num contexto histórico (por exemplo, a ADR-0003 pode citar `pg_dump` ao explicar a mudança de decisão, e o plano em `docs/mvp/` pode citar tudo).

```bash
grep -rn "pg_dump\|pg_restore" docs README.md CONTRIBUTING.md | grep -v "docs/mvp/"
grep -rn "SQLRestriction\|SQLDelete" docs README.md CONTRIBUTING.md src | grep -v "docs/mvp/"
grep -rn "conforme o DER\|DER\b" docs | grep -v "docs/mvp/"
grep -rn "Dockerfile\|--build\|spring-dotenv\|DB_HOST" docs README.md CONTRIBUTING.md | grep -v "docs/mvp/"
grep -rn "Preenchido pela auditoria" docs
grep -rn "TODO" src/main
```

Correções já conhecidas:

1. `docs/adr/0007-cancelamento-de-venda-concluida.md`, em "Consequências": troque "Eles são excluídos diretamente, conforme o DER." por "Eles são excluídos diretamente, conforme o UC12."
2. `docs/database.md`, primeiro parágrafo: mantenha a menção ao `der.jpeg` como registro histórico, como já está.
3. `docs/DOMAIN.md`, seção "MVP Inicial": acrescente os itens "controle de estoque com histórico de movimentações (entradas, ajustes, vendas e devoluções);", "cancelamento de vendas concluídas pelo administrador;" e "backup e restauração dos dados.".
4. `docs/adr/0011-politica-de-migrations.md`, item 5: troque "Isso é documentado no `CONTRIBUTING.md`." por "Isso é documentado no `CONTRIBUTING.md`, seção Migrations."

Registre no relatório cada ocorrência encontrada e o que foi feito com ela.

### 14.5 Roadmap pós-MVP

Crie `docs/roadmap.md` com esta lista, em português, um item por linha, com uma frase de justificativa:

1. Senha no banco embutido (ADR-0004, item 7).
2. Instalador `.msi` para Windows e `.deb` para Linux (ADR-0012).
3. Binários do PostgreSQL para macOS e Linux ARM.
4. UC16: produtos com estoque baixo.
5. Formas de pagamento e cálculo de troco (regra configurável prevista no `DOMAIN.md`).
6. Impressão de comprovante não fiscal.
7. Validação dos dígitos verificadores de CPF e CNPJ (ADR-0009).
8. Restauração de backup entre versões diferentes do schema (ADR-0014).
9. Testes automatizados de interface (por exemplo, TestFX).
10. Aviso de nova versão disponível.

### 14.6 Versão e changelog

1. No `pom.xml`, troque `<version>0.0.1-SNAPSHOT</version>` por `<version>0.1.0</version>`.
2. Crie `CHANGELOG.md`:

```markdown
# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

## [0.1.0] - AAAA-MM-DD

Primeira versão.

### Adicionado

- Caixa (PDV) com código de barras, venda por unidade e por quilo, cliente opcional e atalhos de teclado.
- Cadastros de produtos, categorias, clientes e usuários.
- Controle de estoque com movimentações de entrada, ajuste, venda e devolução.
- Histórico de vendas com filtros e cancelamento de vendas concluídas.
- Backup e restauração.
- PostgreSQL embutido: nenhuma instalação adicional é necessária.
- Pacotes portáteis para Windows e Linux (x64).
```

Deixe `AAAA-MM-DD` como está. A data é preenchida por quem criar a tag.

### 14.7 Validação final (pessoa responsável)

Inclua esta lista no relatório final, **sem executá-la**. Ela depende de push, de máquina limpa e de decisão humana:

1. Fazer push da branch e confirmar o CI verde nos dois sistemas.
2. Baixar o artefato `sells-Windows` e testar numa máquina Windows **sem Java e sem PostgreSQL**: abrir, cadastrar o admin, cadastrar um produto, vender, gerar um backup e restaurá-lo (critério da ADR-0012).
3. Preencher a data no `CHANGELOG.md`, criar a tag `v0.1.0` e publicar a release com os dois arquivos do CI.
4. A partir da tag, a `V1` está congelada (ADR-0011): toda mudança de schema vai numa nova migration.
5. Mudar a versão do `pom.xml` para `0.2.0-SNAPSHOT`.

## Critérios de aceite

1. `./mvnw -B verify` termina com `BUILD SUCCESS`.
2. Os comandos de 14.4 não retornam ocorrências sem justificativa no relatório.
3. Todos os links relativos do `README.md` e do `CONTRIBUTING.md` apontam para arquivos que existem.

## Commit

```
docs: finalize user and contributor documentation for 0.1.0
```
