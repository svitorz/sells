# ADR-0012: Empacotamento com jpackage

- **Status:** Proposta (validação no Windows pendente)
- **Data:** 2026-10-10

## Contexto

O usuário final precisa instalar a aplicação com um único passo. O Java exige um runtime (JRE) e o PostgreSQL embutido (ADR-0003) precisa de binários por sistema operacional.

## Proposta

1. **Ferramenta:** `jpackage`, que gera o instalador nativo de cada sistema operacional.
2. **Runtime:** um JRE mínimo, gerado com `jlink` e incluído no instalador, para que o usuário não precise instalar Java.
3. **Plataforma inicial:** Windows (`.exe` ou `.msi`). Linux e macOS vêm depois, sem mudança de código.
4. **Build:** cada instalador é gerado no sistema operacional correspondente, em GitHub Actions (`windows-latest` primeiro).
5. **Artefatos:** os instaladores são publicados como artefatos do workflow e, nas releases, como assets do GitHub.
6. **Atribuição:** o instalador inclui o aviso de copyright e a licença do PostgreSQL (ADR-0003).

## Critérios para aceitar a proposta

- O instalador do Windows gerado em CI, com a aplicação e o PostgreSQL embutido, fica dentro do limite de 150 MB adicionais.
- A aplicação inicia o banco, executa as migrations e abre a interface em uma máquina Windows limpa, sem Java nem PostgreSQL instalados.

## Consequências

- Cada sistema operacional tem um job próprio no CI.
- O processo de build precisa ser reproduzível e documentado no `CONTRIBUTING.md`.
