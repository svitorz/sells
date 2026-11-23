# ADR-0013: Arquitetura hexagonal em um único módulo

- **Status:** Aceita
- **Data:** 2026-11-23

## Contexto

O projeto deve ser fácil de manter por contribuidores diferentes e precisa permitir a troca de tecnologias (banco de dados, ORM, interface gráfica) sem reescrever as regras de negócio. Até aqui, as entidades JPA eram o modelo de domínio, com setters públicos e nenhuma regra implementada.

## Decisão

1. O código é dividido em quatro camadas, em pacotes de um único módulo Maven:
   - `domain`: entidades e regras de negócio, em Java puro.
   - `application`: casos de uso, portas de saída (interfaces) e objetos de visualização.
   - `infrastructure`: adaptadores que implementam as portas (JPA, PostgreSQL, hash de senha, transação, backup) e a configuração do Spring.
   - `ui`: interface JavaFX, que conversa apenas com a camada `application`.
2. `domain` depende apenas de `java.*`. `application` depende apenas de `domain` e `java.*`. `ui` não depende de `infrastructure` e, do `domain`, usa apenas enums e `DomainException`.
3. As entidades JPA ficam em `infrastructure.persistence.jpa.entity` e são convertidas de e para o domínio por mappers explícitos.
4. As classes da raiz do pacote (`Launcher`, `SellsApplication`, `SellsFxApplication`) são a raiz de composição e podem depender de todas as camadas.
5. As regras são verificadas automaticamente pelo teste `ArchitectureTest` (ArchUnit). Um build que viola as regras falha.

## Motivo

- Regras de negócio sem dependência de framework podem ser testadas sem banco e sem interface, e continuam válidas se a tecnologia mudar.
- Um único módulo mantém o build e o empacotamento simples. O ArchUnit dá a mesma garantia que módulos separados dariam para as regras de dependência.
- Separar as entidades JPA do domínio evita que anotações e limitações do ORM (construtor vazio, setters, carregamento lazy) contaminem as regras.

## Consequências

- Há mais classes: cada entidade persistida tem uma classe de domínio, uma entidade JPA e um mapper.
- Trocar o banco ou o ORM significa reescrever o pacote `infrastructure.persistence`, sem tocar em `domain`, `application` e `ui`.
- Trocar a interface significa reescrever o pacote `ui`.
- Lombok só é permitido nas entidades JPA.
