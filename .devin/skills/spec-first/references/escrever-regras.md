# Escrever regras curtas e testáveis

Uma regra descreve **quem**, **quando** e **o que acontece**, em uma linha (<= 140 caracteres). Se precisa de "e" demais, são duas regras.

| Ruim | Bom |
|---|---|
| O sistema deve gerenciar pedidos de forma eficiente. | `PED-R01` Pedido só pode ser cancelado antes de "enviado". |
| Validar os dados. | `PED-R02` Total = soma dos itens - desconto; nunca negativo. |
| Tratar erros de pagamento. | `PED-R03` Pagamento repetido com a mesma chave de idempotência é ignorado. |
| Usuário tem permissões adequadas. | `PED-R04` Cliente só vê pedidos próprios; admin vê todos. |

## Checagem rápida
- Dá para escrever um teste automatizado que falha se a regra for quebrada? Se não, reescreva.
- Há número, limite, estado ou ator explícito? Evite "adequado", "rápido", "seguro".
- A mesma informação aparece em outro lugar? Mantenha só uma e cite o ID.
- É regra de negócio, e não decisão de implementação (framework, nome de tabela)? Decisões técnicas vão em `nao_funcionais` ou premissas.

## Bordas
Toda borda aponta para uma regra: `"Estoque insuficiente no pagamento -> PED-R07"`. Se não existe regra, crie.

## Estados
`"rascunho -> pago -> enviado -> entregue | cancelado"`. Transições proibidas viram regra (`PED-R01`).

## YAML
Texto com `: ` precisa de aspas. IDs: regra `PED-R01`, não funcional `NF01`, premissa `P01`.
