# ADR-0002: PostgreSQL como único banco de dados

- **Status:** Aceita
- **Data:** 2026-10-10

## Contexto

O sistema precisa de um banco relacional para vendas, estoque e cadastros. Suportar mais de um banco aumentaria o custo de testes, de migrations e de suporte.

## Decisão

O PostgreSQL 16 é o único banco suportado, tanto em desenvolvimento quanto na distribuição.

## Motivo

- O acesso a dados é feito via JPA/Hibernate, então trocar de banco no futuro continua possível sem reescrever a aplicação.
- Regras de integridade (`CHECK`, índices únicos parciais para exclusão lógica, identidade gerada) são usadas nas migrations. Manter um único motor evita divergência de comportamento entre ambientes.
- O PostgreSQL pode ser distribuído embutido na aplicação (ADR-0003), o que elimina a necessidade de instalar um banco separado.

## Consequências

- As migrations podem usar SQL específico do PostgreSQL.
- Os testes de integração rodam contra o mesmo motor usado em produção.
- Instalar o PostgreSQL separadamente deixa de ser necessário na distribuição, mas continua sendo uma opção no modo de desenvolvimento (ADR-0003).
