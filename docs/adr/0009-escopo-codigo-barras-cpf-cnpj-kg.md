# ADR-0009: Escopo do MVP inclui código de barras, CPF/CNPJ e venda por quilo

- **Status:** Aceita
- **Data:** 2026-10-10

## Contexto

Pequenos comércios, como padarias, hortifrutis e açougues, dependem de três recursos que a primeira versão do DER não cobria: leitura de código de barras no caixa, identificação do cliente por CPF ou CNPJ, e venda de produtos por peso.

## Decisão

1. **Código de barras:** `products.barcode`, opcional e único entre produtos ativos (ADR-0006).
2. **CPF/CNPJ:** `customers.cpf_cnpj`, opcional e único entre clientes ativos. É armazenado somente com dígitos, com 11 (CPF) ou 14 (CNPJ) caracteres. Uma constraint no banco valida o formato.
3. **Venda por quilo:** `products.unit` é `UN` (unidade) ou `KG` (quilo). A quantidade de itens e o estoque são `NUMERIC(10,3)`.
4. **Regra de unidade:** produtos `UN` aceitam apenas quantidades inteiras. Essa regra é validada na aplicação.

## Motivo

- O código de barras acelera o caixa e reduz erros de digitação, que são o principal gargalo de um PDV simples.
- O CPF ou CNPJ permite identificar o cliente em vendas futuras e é o dado mínimo para emissão de documentos fiscais, que continua fora do escopo.
- Sem venda por quilo, produtos a granel não podem ser vendidos corretamente.

## Consequências

- A quantidade em `NUMERIC(10,3)` permite frações de até três casas decimais. Isso cobre gramas em produtos vendidos por quilo.
- Validar o CPF ou CNPJ pelos dígitos verificadores não faz parte desta decisão. Pode ser adicionado depois sem mudança de schema.
