# ADR-0001: Licença GPLv3

- **Status:** Aceita
- **Data:** 2026-10-10

## Contexto

O projeto é uma ferramenta de vendas e controle de estoque open-source, voltada a pequenos comerciantes. Precisamos escolher uma licença antes da primeira versão pública.

## Decisão

O projeto é distribuído sob a **GNU General Public License v3.0** (arquivo `LICENSE`).

## Motivo

- Melhorias feitas por terceiros que forem distribuídas continuam disponíveis para a comunidade, em vez de serem fechadas em um produto comercial.
- A cópia do sistema para pequenos comerciantes continua gratuita e acessível, mesmo quando alguém ganha dinheiro com serviços em cima dela.

## Consequências

- Dependências precisam ser compatíveis com a GPLv3. As atuais (Spring, Hibernate, JavaFX, PostgreSQL JDBC, Lombok, Flyway e embedded-postgres) são compatíveis.
- Distribuições binárias precisam disponibilizar o código-fonte correspondente.
