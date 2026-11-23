# ADR-0004: Ciclo de vida do banco embutido

- **Status:** Aceita (implementada, exceto o item 7)
- **Data:** 2026-10-10

## Contexto

No modo `embedded` (ADR-0003), a aplicação precisa gerenciar um processo PostgreSQL que pertence ao usuário final. Isso inclui onde os dados ficam, como a porta é escolhida, como o banco é iniciado e encerrado, e o que acontece se a aplicação for fechada de forma abrupta.

## Decisão

1. **Dados:** ficam em um diretório do usuário, `~/.sells/pgdata`. A localização pode ser alterada por propriedade. Os binários extraídos ficam em `~/.sells/runtime` e o log em `~/.sells/logs/sells.log`. O diretório pode ser alterado por `sells.data.dir` ou `SELLS_DATA_DIR`.
2. **Porta:** escolhida automaticamente entre as livres. Nenhuma porta fixa é usada.
3. **Início:** o PostgreSQL sobe antes do contexto do Spring. Só então o `DataSource` é criado e as migrations são executadas.
4. **Encerramento:** a parada acontece no encerramento da aplicação, por parada limpa (`pg_ctl stop -m fast`).
5. **Limpeza do diretório:** a biblioteca nunca deve limpar o diretório de dados automaticamente (`cleanDataDirectory=false`). A limpeza só acontece por ação explícita.
6. **Instância única:** apenas uma aplicação pode usar um diretório de dados por vez. A proteção é feita por um arquivo de lock da aplicação, com `FileChannel.tryLock()`. Não basta confiar no lock da biblioteca, porque ele só vale dentro da mesma JVM.
7. **Credenciais:** o banco não deve aceitar conexões sem senha de outros usuários do sistema operacional. A senha é gerada na primeira inicialização e guardada no diretório de dados. Esse requisito é pendente de implementação, porque a biblioteca inicia o banco com autenticação `trust` em localhost. Enquanto isso não é implementado, o servidor aceita conexões apenas em `localhost` (`listen_addresses`).
8. **Queda abrupta:** na próxima inicialização, o PostgreSQL faz a recuperação automática. O spike confirmou isso com `kill -9`, com dados íntegros e recuperação em 0,11 s.
9. **Durabilidade:** a biblioteca inicia o PostgreSQL com `fsync` e `synchronous_commit` desligados, o que é adequado para testes, mas não para dados de vendas. A aplicação religa `fsync`, `synchronous_commit` e `full_page_writes`. O teste `runsWithDurableSettings` garante isso.

## Motivo

- Sem a limpeza automática, perder dados por engano não é possível. O comportamento padrão da biblioteca, de apagar no `close()`, seria perigoso para um sistema de vendas.
- Um arquivo de lock da aplicação dá uma mensagem clara ao usuário, em vez de um erro técnico do PostgreSQL.
- Credenciais geradas evitam que outro usuário do mesmo computador acesse os dados de vendas.

## Consequências

- O componente de ciclo de vida precisa ter testes para: início com dados limpos, reinício com dados existentes, bloqueio de segunda instância e recuperação após queda.
- A porta e a senha passam a ser internas. A configuração do usuário fica restrita ao que ele vê (por exemplo, a localização dos dados).
- A implementação está em `infrastructure.database.DatabaseBootstrap`. Os testes de início com dados limpos, reinício e bloqueio de segunda instância estão em `DatabaseBootstrapTest`. A recuperação após queda foi validada no spike da ADR-0003 e não tem teste automatizado.
