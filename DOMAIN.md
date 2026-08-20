# DOMAIN.md

## Objetivo

No Brasil, existem centenas de empresas e aplicativos desktop destinados
ao gerenciamento de operações comerciais, como cadastro de produtos,
vendas, clientes e controle de estoque.

Muitos desses sistemas possuem custos que podem dificultar sua adoção por
pequenos comerciantes, principalmente durante o início de suas atividades.

O objetivo deste projeto é desenvolver uma ferramenta de vendas desktop,
open-source e gratuita, permitindo que qualquer pessoa possa utilizá-la,
estudá-la, modificá-la e contribuir com seu desenvolvimento.

O projeto busca inicialmente fornecer um núcleo simples para operações
comerciais, evitando incorporar funcionalidades complexas antes que o
núcleo de vendas esteja consolidado.

## Entidades do MVP

- User
- Customer
- Product
- Category
- Sale
- SaleItem

## MVP Inicial

O MVP deverá permitir:

- gerenciamento de usuários;
- gerenciamento de clientes;
- gerenciamento de produtos;
- gerenciamento de categorias;
- criação de vendas;
- inclusão e remoção de produtos de uma venda;
- alteração da quantidade dos produtos;
- cálculo do total da venda;
- finalização de uma venda;
- consulta do histórico de vendas.

O MVP deverá possuir uma interface gráfica desktop que permita realizar
essas operações sem necessidade de interação direta com o banco de dados.

## Fora do escopo inicial

Não fazem parte do MVP:

- emissão de documentos fiscais;
- integração com sistemas fiscais;
- controle financeiro;
- contas a pagar;
- contas a receber;
- CRM avançado;
- e-commerce;
- integração com marketplaces;
- multiempresa;
- multifilial;
- relatórios avançados;
- sistema de promoções;
- programa de fidelidade;
- integrações externas.

Essas funcionalidades poderão ser consideradas posteriormente.

## Regras de domínio

As regras de domínio deverão ser divididas entre:

### Invariantes

Regras necessárias para preservar a consistência do sistema.

Exemplos:

- uma quantidade de SaleItem deve ser maior que zero;
- um preço não pode ser negativo;
- uma venda deve possuir pelo menos um item para ser finalizada;
- uma SaleItem deve estar associada a um Product;
- uma SaleItem deve estar associada a uma Sale.

### Regras configuráveis

Comportamentos que poderão variar de acordo com o estabelecimento.

Exemplos futuros:

- obrigatoriedade de cadastro do cliente;
- limite de desconto;
- necessidade de autorização para cancelamento;
- formas de pagamento permitidas.

## Princípios

O MVP deverá priorizar:

- simplicidade;
- consistência dos dados;
- facilidade de utilização;
- facilidade de manutenção;
- código aberto;
- extensibilidade.

O projeto não deverá incorporar funcionalidades ou regras que não sejam
necessárias para o escopo atual sem que elas sejam previamente justificadas
e documentadas.
