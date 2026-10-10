# Casos de uso

Este documento descreve os casos de uso do sistema, com atores, fluxo principal e regras. As regras de negócio são detalhadas em `DOMAIN.md`, e as decisões que as fundamentam estão em `adr/`.

## Atores

| Ator | Role no sistema | Descrição |
|---|---|---|
| Administrador | `ADMIN` | Gerencia cadastros, estoque e cancelamentos. Tem acesso a todas as funções. |
| Operador de caixa | `USER` | Registra vendas e consulta cadastros e histórico. |
| Sistema | — | Executa ações automáticas, como a criação do primeiro administrador. |

O cliente não é ator, porque não usa o sistema.

## Resumo

| ID | Caso de uso | Ator | Escopo |
|---|---|---|---|
| UC01 | Autenticar no sistema | Todos | MVP |
| UC02 | Configurar o primeiro administrador | Sistema | MVP |
| UC03 | Gerenciar usuários | Administrador | MVP |
| UC04 | Gerenciar categorias | Administrador | MVP |
| UC05 | Gerenciar produtos | Administrador | MVP |
| UC06 | Buscar produtos | Operador, Administrador | MVP |
| UC07 | Gerenciar clientes | Operador, Administrador | MVP |
| UC08 | Criar rascunho de venda | Operador | MVP |
| UC09 | Adicionar item ao rascunho | Operador | MVP |
| UC10 | Alterar quantidade ou remover item do rascunho | Operador | MVP |
| UC11 | Vincular ou trocar o cliente do rascunho | Operador | MVP |
| UC12 | Excluir rascunho | Operador | MVP |
| UC13 | Finalizar venda | Operador | MVP |
| UC14 | Cancelar venda concluída | Administrador | MVP |
| UC15 | Consultar histórico de vendas | Operador, Administrador | MVP |
| UC16 | Consultar produtos com estoque baixo | Administrador | Pós-MVP |
| UC17 | Registrar entrada ou ajuste de estoque | Administrador | MVP |
| UC18 | Consultar movimentações de estoque | Administrador | MVP |
| UC19 | Exportar e restaurar backup | Administrador | MVP |

---

## UC01 Autenticar no sistema

- **Ator:** todos.
- **Fluxo principal:** o usuário informa username e senha. O sistema valida as credenciais e libera as funções de acordo com a role.
- **Regras:**
  - Usuários inativos (`active = false`, ADR-0006) não podem autenticar.
  - A senha é armazenada com hash, nunca em texto puro.

## UC02 Configurar o primeiro administrador

- **Ator:** sistema.
- **Gatilho:** a aplicação é iniciada com o banco sem nenhum usuário ativo.
- **Fluxo principal:** o sistema solicita nome, e-mail, username e senha do primeiro administrador e cria o usuário com role `ADMIN`.
- **Regras:** o fluxo só é exibido enquanto não houver nenhum usuário ativo.

## UC03 Gerenciar usuários

- **Ator:** Administrador.
- **Fluxo principal:** criar, editar, trocar a senha, desativar e reativar usuários.
- **Regras:**
  - Usuários não são excluídos. Desativar define `active = false` (ADR-0006), e os registros de vendas e movimentações do usuário são mantidos.
  - Reativar define `active = true`.
  - E-mail e username são únicos, mesmo entre usuários inativos.
  - O sistema não permite desativar o último administrador ativo.

## UC04 Gerenciar categorias

- **Ator:** Administrador.
- **Fluxo principal:** criar, editar e excluir categorias. Um produto pode pertencer a várias categorias, e uma categoria pode ter vários produtos.
- **Regras:**
  - Nome é obrigatório.
  - A exclusão é física (ADR-0006). Antes de confirmar, o sistema avisa quantos produtos perderão o vínculo com a categoria.

## UC05 Gerenciar produtos

- **Ator:** Administrador.
- **Fluxo principal:** cadastrar, editar, definir categorias e excluir produtos.
- **Campos:** nome, código de barras (opcional), preço, unidade (`UN` ou `KG`), descrição, estoque inicial e categorias.
- **Regras:**
  - Preço não pode ser negativo.
  - O código de barras é único entre produtos ativos (ADR-0009).
  - A unidade `UN` aceita apenas quantidades inteiras. A unidade `KG` aceita até três casas decimais.
  - A exclusão é lógica (ADR-0006). Produtos excluídos continuam nas vendas e movimentações antigas.
  - Alterar o estoque diretamente não é permitido. Mudanças de estoque passam pelo UC17.

## UC06 Buscar produtos

- **Atores:** Operador, Administrador.
- **Fluxo principal:** buscar por nome, por código de barras ou por categoria. A leitura de código de barras deve retornar o produto de forma imediata.
- **Regras:** produtos excluídos não aparecem na busca.

## UC07 Gerenciar clientes

- **Atores:** Operador, Administrador.
- **Fluxo principal:** cadastrar, editar e excluir clientes.
- **Campos obrigatórios:** nome e telefone.
- **Campos opcionais:** e-mail, data de nascimento, CPF ou CNPJ, e endereço (rua, número, complemento, bairro, cidade, UF e CEP).
- **Regras:**
  - CPF ou CNPJ, quando informado, deve ter 11 ou 14 dígitos e é único entre clientes ativos (ADR-0009).
  - A exclusão é lógica (ADR-0006).
  - O cadastro é opcional na venda. Uma venda pode ser feita sem cliente.

## UC08 Criar rascunho de venda

- **Ator:** Operador.
- **Fluxo principal:** o operador inicia uma nova venda. O sistema cria um rascunho (`DRAFT`), associado ao usuário logado e com subtotal zero.
- **Regras:** um rascunho não altera o estoque (ADR-0005).

## UC09 Adicionar item ao rascunho

- **Ator:** Operador.
- **Fluxo principal:** o operador seleciona um produto (pela busca ou pelo código de barras) e informa a quantidade. O sistema adiciona o item e recalcula o subtotal.
- **Regras:**
  - A quantidade deve ser maior que zero.
  - Produtos `UN` aceitam apenas quantidades inteiras.
  - O preço é copiado do produto no momento da inclusão (`unit_price`).
  - Se o produto já estiver no rascunho, as quantidades são somadas em uma única linha. Um produto aparece no máximo uma vez por venda (`UNIQUE` em `sale_items`).
  - Se a quantidade exceder o estoque disponível, o sistema mostra um aviso, mas permite a inclusão. O bloqueio acontece na finalização (ADR-0005).

## UC10 Alterar quantidade ou remover item do rascunho

- **Ator:** Operador.
- **Fluxo principal:** o operador altera a quantidade de um item ou remove o item do rascunho. O subtotal é recalculado.
- **Regras:** o rascunho precisa estar em `DRAFT`. Uma venda concluída ou cancelada não pode ter itens alterados.

## UC11 Vincular ou trocar o cliente do rascunho

- **Ator:** Operador.
- **Fluxo principal:** o operador seleciona um cliente cadastrado ou deixa a venda sem cliente.
- **Regras:** a troca de cliente só é permitida em `DRAFT`.

## UC12 Excluir rascunho

- **Ator:** Operador.
- **Fluxo principal:** o operador cancela um rascunho. O rascunho e seus itens são excluídos.
- **Regras:**
  - Só rascunhos podem ser excluídos. Vendas concluídas seguem o UC14.
  - Como o rascunho nunca alterou o estoque, nenhuma movimentação é criada.

## UC13 Finalizar venda

- **Ator:** Operador.
- **Fluxo principal:**
  1. O operador confirma a finalização do rascunho.
  2. O sistema verifica se há pelo menos um item.
  3. O sistema verifica o estoque de cada item, com bloqueio no produto para evitar vendas simultâneas.
  4. O sistema cria uma movimentação `SALE` para cada item, com quantidade negativa.
  5. O sistema atualiza o saldo de estoque de cada produto.
  6. O subtotal é congelado e o status passa para `COMPLETED`.
- **Regras:**
  - A venda precisa ter pelo menos um item (DOMAIN).
  - Se algum item exceder o estoque, a finalização é bloqueada e o sistema informa o produto e a quantidade disponível (ADR-0005).
  - Estoque negativo nunca é permitido (ADR-0005).
  - Todas as alterações acontecem em uma única transação.

## UC14 Cancelar venda concluída

- **Ator:** Administrador.
- **Fluxo principal:**
  1. O administrador seleciona uma venda concluída e informa o motivo do cancelamento.
  2. O sistema muda o status para `CANCELLED` e registra data e responsável.
  3. Para cada item, o sistema cria uma movimentação `RETURN` e devolve a quantidade ao estoque.
- **Regras:**
  - Somente administradores podem cancelar (ADR-0007).
  - O motivo é obrigatório (ADR-0007).
  - Vendas canceladas não podem ser editadas nem finalizadas novamente (DOMAIN).

## UC15 Consultar histórico de vendas

- **Atores:** Operador, Administrador.
- **Fluxo principal:** listar vendas com filtros por período, status, cliente e usuário. Ao selecionar uma venda, o sistema mostra os itens, o subtotal, o cliente, o responsável e, se houver, os dados do cancelamento.
- **Regras:** vendas canceladas mantêm o motivo e o responsável pelo cancelamento visíveis no histórico.

## UC16 Consultar produtos com estoque baixo

- **Ator:** Administrador.
- **Escopo:** pós-MVP.
- **Fluxo principal:** listar produtos cujo estoque está abaixo de um limite configurável.

## UC17 Registrar entrada ou ajuste de estoque

- **Ator:** Administrador.
- **Fluxo principal:**
  - **Entrada:** o administrador informa o produto e a quantidade recebida. O sistema cria uma movimentação `ENTRY`.
  - **Ajuste:** o administrador informa o produto, a diferença (positiva ou negativa) e o motivo. O sistema cria uma movimentação `ADJUSTMENT`.
- **Regras:**
  - Fornecedores não são registrados no MVP (ADR-0008). A origem da entrada pode ser descrita no campo de observações.
  - Ajustes exigem observações preenchidas (ADR-0005).
  - O saldo do produto nunca pode ficar negativo.

## UC18 Consultar movimentações de estoque

- **Ator:** Administrador.
- **Fluxo principal:** listar as movimentações de um produto, em ordem cronológica, com tipo, quantidade, data, responsável e venda relacionada, quando houver.
- **Regras:** movimentações são imutáveis. Não há edição nem exclusão (ADR-0005).

## UC19 Exportar e restaurar backup

- **Ator:** Administrador.
- **Fluxo principal:**
  - **Exportar:** o sistema gera um arquivo de backup do banco de dados, em um local escolhido pelo administrador.
  - **Restaurar:** o administrador escolhe um arquivo de backup. O sistema substitui os dados atuais, depois de confirmar a operação.
- **Regras:**
  - A restauração exige confirmação explícita, porque substitui todos os dados.
  - Os binários `pg_dump` e `pg_restore` são fornecidos junto com a aplicação (ADR-0003).
