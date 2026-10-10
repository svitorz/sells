# ADR-0005: Estoque com movimentações imutáveis e baixa na finalização

- **Status:** Aceita
- **Data:** 2026-10-10

## Contexto

O sistema controla estoque. Precisamos decidir quando a quantidade de um produto diminui e como manter o histórico das alterações.

## Decisão

1. **Histórico:** toda alteração de estoque gera um registro em `stock_movements`. Os registros são imutáveis: não há update nem delete.
2. **Tipos de movimentação:** `ENTRY` (entrada), `SALE` (saída por venda), `RETURN` (devolução por cancelamento de venda) e `ADJUSTMENT` (ajuste manual).
3. **Saldo:** `products.stock` é o saldo atual. Ele é atualizado na mesma transação que cria as movimentações.
4. **Baixa:** acontece somente na finalização da venda.
5. **Estoque negativo:** não é permitido. Isso é garantido pela constraint `CHECK (stock >= 0)` e pela validação na aplicação.
6. **Adicionar item ao rascunho:** mostra o estoque disponível como aviso, sem bloquear. Um rascunho não reserva estoque.
7. **Finalizar:** bloqueia se algum item exceder o estoque disponível. A verificação usa lock no produto ou controle de versão (`@Version`), para que duas vendas simultâneas não vendam o mesmo item.
8. **Ajuste manual:** exige `notes` preenchido, para que toda correção tenha justificativa.

## Motivo

- A baixa na finalização evita que rascunhos abandonados travem o estoque.
- O histórico permite auditar divergências entre o saldo e o que foi vendido ou recebido.
- Movimentações imutáveis garantem que o histórico não possa ser alterado depois do fato.

## Consequências

- Um rascunho pode mostrar um item disponível e, na finalização, a venda ser bloqueada. Isso é aceito e a mensagem precisa ser clara.
- O saldo e as movimentações precisam ser sempre consistentes. Isso é garantido por transação e deve ser coberto por testes.
