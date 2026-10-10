# ADR-0003: Distribuição com PostgreSQL embutido

- **Status:** Aceita
- **Data:** 2026-10-10

## Contexto

O público-alvo são pequenos comerciantes, que não devem precisar instalar e configurar um banco de dados para usar o sistema. Ao mesmo tempo, o projeto precisa de PostgreSQL (ADR-0002).

Alternativas consideradas:

| Opção | Avaliação |
|---|---|
| A. Instalador oficial do PostgreSQL | Exige duas instalações e configuração manual de serviço. Aceitável para técnicos, não para o público-alvo. |
| B. Docker Desktop + compose | Pesado e dependente de Docker. Não é adequado ao usuário final. |
| C. PostgreSQL embutido, gerenciado pela aplicação | Instalação em um passo. Continua sendo PostgreSQL real. Exige engenharia de empacotamento e ciclo de vida. |

## Decisão

Adotamos a opção C:

- **Modo `embedded`** (padrão da distribuição): a aplicação inicia e encerra o PostgreSQL por conta própria.
- **Modo `external`** (desenvolvimento): a aplicação se conecta a um PostgreSQL existente, configurado por variáveis de ambiente. Pode ser o container do `compose.yml`.

A biblioteca usada é `io.zonky.test:embedded-postgres` (versão 2.2.2), com binários do PostgreSQL 16.15.0 declarados por profile Maven, um por sistema operacional. A biblioteca traz por padrão binários de todas as plataformas e uma versão antiga do PostgreSQL. Por isso, as dependências transitivas são excluídas e apenas o binário da plataforma alvo é incluído.

## Motivo

- A instalação passa a exigir apenas o instalador do sistema.
- O banco continua sendo PostgreSQL, então as migrations e os testes são os mesmos nos dois modos.

## Resultados do spike (Linux, 2026-10-10)

| Medição | Resultado | Critério |
|---|---|---|
| Start com dados limpos (initdb e boot) | 1,07 s | até 10 s |
| Flyway (migration V1) | 0,46 s | — |
| Parada limpa | 0,10 s | — |
| Restart com dados existentes | 0,11 s | — |
| Recuperação após `kill -9` | 0,11 s, dados íntegros | — |
| Memória do postmaster ocioso (RSS) | ~60 MB | — |
| Jar do binário Linux | 14,7 MB | — |
| Jar do binário Windows | 23,4 MB | instalador adicional até 150 MB |

Validação no Windows: pendente, via GitHub Actions (`windows-latest`).

Achados que afetam a implementação:

- `close()` apaga o diretório de dados quando `cleanDataDirectory` é `true`. A mesma flag controla a limpeza no start. Por isso, a aplicação usa sempre `false` e faz a limpeza explicitamente, quando necessário.
- O lock da biblioteca só protege dentro da mesma JVM (`OverlappingFileLockException`). A proteção entre processos precisa de verificação própria (ADR-0004).

## Atribuição

O PostgreSQL usa licença permissiva (PostgreSQL License). A distribuição precisa incluir o aviso de copyright e o texto da licença junto com os binários.

## Consequências

- O instalador precisa de binários por sistema operacional. Por isso, a geração do instalador exige um build por sistema, em CI (ADR-0012).
- Atualizar a versão maior do PostgreSQL exige um procedimento de dump e restore. Será documentado em uma ADR própria antes da primeira distribuição.
- Backup e restauração passam a ser responsabilidade do sistema (caso de uso UC19).
