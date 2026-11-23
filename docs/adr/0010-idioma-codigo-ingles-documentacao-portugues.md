# ADR-0010: Código em inglês, documentação em português

- **Status:** Aceita
- **Data:** 2026-10-10

## Contexto

O projeto é open-source e aceita contribuições. Ao mesmo tempo, o público-alvo, o domínio e a documentação são brasileiros.

## Decisão

1. **Código** (classes, métodos, campos, variáveis, comentários de código e mensagens de commit): inglês.
2. **Tabelas e colunas** do banco: inglês, em `snake_case`.
3. **Documentação** (README, `CONTRIBUTING.md`, `docs/`, ADRs): português. A documentação em outros idiomas pode ser adicionada no futuro.
4. **Textos exibidos ao usuário** (interface e mensagens): português.

## Motivo

- Nomes em inglês facilitam a leitura e a contribuição de desenvolvedores de outros países e o uso de bibliotecas.
- A documentação em português atende ao público brasileiro, que é o foco do projeto.

## Consequências

- Os termos do domínio são traduzidos no dicionário de dados (`docs/database.md`), para que a relação entre o termo em português e o nome no código fique clara. Exemplo: "rascunho" (`DRAFT`), "venda" (`Sale`).
- O glossário do domínio fica em `docs/DOMAIN.md`.
