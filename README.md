# Sells

Sistema de vendas desktop, livre e gratuito (GPLv3), para pequenos comércios.

> Projeto em desenvolvimento. O plano até a primeira versão está em `docs/mvp/`.

## Desenvolvimento

Requisitos: JDK 21 ou mais novo. Docker não é necessário.

Rodar os testes (o PostgreSQL de teste é embutido e iniciado automaticamente):

    ./mvnw -B verify

A arquitetura está descrita em docs/adr/0013-arquitetura-hexagonal.md.
