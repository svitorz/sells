# Sistema de Vendas open source

Para configurar o ambiente:

```bash
cp .env.example .env 

```

Após isso, basta configurar as variáveis de ambiente no seu .env, e subir a aplicação com:

```bash
docker compose up -d --build
```

## Executar a interface JavaFX

Com o PostgreSQL disponível e as variáveis do `.env` configuradas, execute:

```bash
./mvnw javafx:run
```

O JavaFX inicia a janela e o Spring Boot inicializa o contexto dentro do ciclo de vida
da aplicação. Para executar apenas os testes, use `./mvnw test`.

> [!NOTE]
> As recomendações de configuração (default) estão no .env.example, utilize se estiver em ambiente de testes apenas.
