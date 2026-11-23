# ADR-0007: Cancelamento de venda concluída

- **Status:** Aceita
- **Data:** 2026-10-10

## Contexto

Uma venda concluída pode precisar ser cancelada, por exemplo por erro de cadastro ou devolução. Essa operação altera estoque e precisa ser auditável.

## Decisão

1. **Permissão:** somente usuários com a role `ADMIN` podem cancelar uma venda concluída.
2. **Motivo obrigatório:** o cancelamento exige um texto explicando o motivo (`cancel_reason`).
3. **Auditoria:** o cancelamento registra `cancelled_at` e `cancelled_by` (usuário que cancelou).
4. **Estoque:** para cada item da venda, é criada uma movimentação `RETURN`, que devolve a quantidade ao estoque.
5. **Status:** a venda passa para `CANCELLED`. Uma venda cancelada não pode ser editada nem finalizada novamente.
6. **Constraint:** o banco garante que uma venda `CANCELLED` tenha `cancelled_at`, `cancelled_by` e `cancel_reason` preenchidos.

## Motivo

- Cancelar uma venda sem devolver o estoque deixaria o saldo errado.
- Um motivo obrigatório e a identificação de quem cancelou tornam o cancelamento rastreável.
- Restringir a operação ao Admin evita cancelamentos feitos por engano ou por má-fé no dia a dia do caixa.

## Consequências

- O cancelamento de uma venda concluída é uma operação em transação única: muda o status, cria as movimentações `RETURN` e atualiza o saldo.
- Rascunhos (`DRAFT`) não passam por esse fluxo. Eles são excluídos diretamente, conforme o DER.
