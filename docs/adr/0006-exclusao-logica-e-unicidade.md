# ADR-0006: Exclusão lógica, desativação e unicidade

- **Status:** Aceita
- **Data:** 2026-10-10

## Contexto

Produtos e clientes aparecem em vendas e movimentações de estoque, então excluí-los fisicamente quebraria o histórico. Usuários também são referenciados por vendas e movimentações. Já categorias são apenas uma classificação, sem histórico próprio. Identificadores como e-mail, username e código de barras precisam continuar únicos.

## Decisão

1. **Produtos e clientes** têm exclusão lógica pela coluna `deleted_at`. Registros excluídos são ocultados das consultas padrão pela anotação `@SQLRestriction`.
2. **Auditoria:** produtos e clientes têm `created_at`, `updated_at` e `deleted_at`. Usuários não têm campos de auditoria nesta versão.
3. **Usuários** não são excluídos nem recebem `deleted_at`. São desativados pelo campo `active`. Um usuário inativo não pode autenticar, e a reativação define `active` como verdadeiro.
4. **Categorias** são excluídas fisicamente. Os vínculos com produtos são removidos em cascata, pela tabela `product_category`.
5. **Unicidade:**
   - `users.email` e `users.username` são únicos sempre, sem considerar `active`.
   - `products.barcode` e `customers.cpf_cnpj` são únicos apenas entre registros ativos (`WHERE deleted_at IS NULL`), por índice único parcial.

## Motivo

- Produtos e clientes precisam manter o histórico de vendas e movimentações, e por isso têm exclusão lógica.
- Usuários são referenciados por vendas e movimentações. Desativar mantém esse vínculo, sem o custo de uma exclusão lógica.
- E-mail e username continuam reservados, porque um usuário inativo ainda é um usuário que pode ser reativado.
- Categorias não têm histórico próprio. Excluí-las fisicamente é mais simples e não perde informação importante.
- Com a unicidade parcial, um cliente ou produto excluído não impede o cadastro de outro com o mesmo CPF/CNPJ ou código de barras.

## Consequências

- Consultas em `products` e `customers` respeitam a restrição de registros ativos. A anotação `@SQLRestriction` faz isso automaticamente nas consultas via JPA.
- O índice único parcial não é reconhecido pelo `@Column(unique = true)`. Por isso, a unicidade é garantida no banco pela migration, e não pela anotação.
- Excluir uma categoria remove silenciosamente seus vínculos com produtos. A interface deve avisar o administrador antes de confirmar a exclusão.
