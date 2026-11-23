# Revisão adversarial (faça antes de codar)

Releia a spec como alguém que quer achar o bug antes de ele existir. Para cada domínio, responda em silêncio e **corrija a spec** onde houver lacuna:

1. **Falha no meio:** se a operação A deu certo e B falhou, qual é o estado final? Existe regra de compensação ou transação?
2. **Repetição:** o mesmo pedido chega duas vezes (duplo clique, retry, webhook duplicado). O que acontece?
3. **Concorrência:** dois usuários editam o mesmo registro; qual vence?
4. **Estados:** existe transição possível que não está proibida nem permitida? Existe estado sem saída?
5. **Permissões:** para cada ação, quem pode e quem não pode? Um ator tenta agir sobre dado de outro.
6. **Vazios e limites:** zero itens, um item, muitos itens, texto enorme, valor zero ou negativo.
7. **Tempo:** prazos, expiração, fuso, virada de dia/mês, datas no passado.
8. **Dinheiro e números:** arredondamento, moeda, ordem de descontos/impostos.
9. **Exclusão:** o que acontece com dados relacionados, histórico e unicidade?
10. **Contradições:** duas regras (ou regra e não funcional) não podem ser verdadeiras ao mesmo tempo?
11. **Ator invisível:** jobs agendados, webhooks, admin/suporte e integrações também são atores com permissões.
12. **Fora de escopo:** algo que o usuário provavelmente espera está faltando no escopo e também no fora de escopo?

Saída da revisão: regras novas ou ajustadas, premissas novas (`P..`) e dúvidas para o usuário. Não escreva relatório separado.
