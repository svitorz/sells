# ADR-0008: Fornecedor fora do MVP

- **Status:** Aceita
- **Data:** 2026-10-10

## Contexto

Entradas de estoque podem ter um fornecedor de origem. Modelar fornecedores exige cadastro, compras e, possivelmente, contas a pagar. O `DOMAIN.md` já coloca contas a pagar fora do escopo inicial.

## Decisão

Não existe entidade de fornecedor no MVP. As entradas de estoque são registradas como movimentações `ENTRY`, com a origem descrita no campo `notes`, quando necessário.

## Motivo

- O MVP precisa focar no núcleo de vendas e estoque, como definido no `DOMAIN.md`.
- Um cadastro de fornecedor sem compras e pagamentos teria pouco valor, e aumentaria o escopo sem uso real.
- Quando o sistema evoluir para compras, a modelagem de fornecedores será feita com base em uso real, sem alterar as movimentações existentes.

## Consequências

- Não é possível consultar o histórico de compras por fornecedor no MVP.
- Esta decisão deve ser revisada quando compras ou contas a pagar entrarem no escopo.
