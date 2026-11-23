# ADR-0011: Política de migrations

- **Status:** Aceita
- **Data:** 2026-10-10

## Contexto

O Flyway aplica migrations em ordem de versão e registra um checksum de cada uma. Mudar uma migration já aplicada faz o Flyway falhar na inicialização. O número da versão identifica a release do schema, não a quantidade de migrations.

## Decisão

1. **Antes da primeira release**, todo o schema inicial fica em uma única migration, `V1__CREATE_INITIAL_SCHEMA.sql`. Ela pode ser reescrita durante o desenvolvimento.
2. **Depois da primeira release**, a `V1` nunca mais é alterada. Mudanças passam a ser feitas em novas versões (`V2`, `V3`...).
3. **Migration aplicada nunca é editada**, mesmo depois da release. Correções são feitas em uma nova migration.
4. **Nomenclatura:** `V<versão>__<DESCRICAO_EM_MAIUSCULAS>.sql`.
5. **Ambiente de dev:** quem já aplicou uma `V1` antiga deve recriar o banco. Isso é documentado no `CONTRIBUTING.md`.

## Motivo

- Enquanto não há usuários, reescrever a `V1` é o caminho mais simples e mantém um histórico limpo.
- Depois da release, editar migrations aplicadas quebraria a inicialização nas instalações existentes.

## Consequências

- A primeira release precisa ser marcada explicitamente, para que a regra passe a valer.
- Qualquer alteração de schema depois da release exige uma nova migration, mesmo para correções pequenas.
