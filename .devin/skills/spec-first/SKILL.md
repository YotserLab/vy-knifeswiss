---
name: spec-first
description: Protocolo spec-first que obriga o agente a criar uma spec estruturada, enxuta e verificável (domínios, regras de negócio com ID, estados, permissões, checklist por tipo de app) ANTES de escrever código, com portão de validação por script e rastreabilidade de regra a código e teste. Use SEMPRE que o usuário pedir para criar um app, sistema, plataforma, SaaS, API ou uma feature grande/complexa (mesmo com prompt curto como "faz um app de pedidos"), ou pedir spec, especificação, requisitos, regras de negócio, planejamento ou PRD. Também use ao retomar um projeto que já tem pasta spec/. Não use para bugfix pequeno ou ajuste visual. Use for any new app/system or large feature request, or when asked for specs/requirements/business rules before coding.
---

# spec-first

Objetivo: o agente não preenche lacunas com suposições. Tudo que importa vira **dado curto e verificável** em `spec/`, e um script (`scripts/check.py`) barra o código até a spec estar completa.

`check.py` fica em `scripts/` desta skill. Abaixo, `CHECK` = `python <pasta-da-skill>/scripts/check.py` (rode da raiz do projeto). Requer PyYAML.

## Princípios (não negociáveis)
1. **Dados, não prosa.** Regra = uma linha, até 140 caracteres, testável, um fato só.
2. **Fonte única.** Cada fato mora em um lugar; plano, tarefas, código e testes só citam IDs (`PED-R01`).
3. **Nada implícito.** O que não foi confirmado vira premissa (`P01`) ou dúvida; o que não se aplica vira `na: motivo`.
4. **Proporcional.** O porte (P/M/G) define o rigor. Não infle spec de app pequeno.
5. **A spec vem antes do código** e é atualizada antes de qualquer mudança de comportamento.

## Fluxo

### 0. Triagem
Bugfix pequeno ou ajuste visual: pule a skill. Caso contrário, escolha o porte: **P** (1 a 2 domínios, poucas telas), **M** (até 6 domínios), **G** (mais que isso).

### 1. Entrevista (uma rodada, no máximo 8 perguntas)
Leia o pedido e o que já existe no repositório. Pergunte **só** o que muda regras ou arquitetura (papéis, dinheiro, dados pessoais, integrações, multiusuário, fora de escopo). Para cada pergunta, ofereça uma sugestão padrão ("assumo X, ok?") para o usuário responder rápido. Se o usuário não puder responder agora, **não bloqueie**: registre como premissa em `spec.yaml` (`P01: ...`) e siga.

### 2. Esqueleto
```
CHECK init <P|M|G>            # cria spec/, checklist base, protocolo no AGENTS.md
CHECK packs list              # veja os packs e quando se aplicam
CHECK packs add auth lgpd     # inclua os que se aplicam
CHECK dominio pedidos PED     # um por domínio de negócio (prefixo de 2-6 letras)
```
Packs obrigatórios para decidir: `base` e `seguranca` (qualquer porte); os demais em M/G. Pack que não se aplica vai em `packs_descartados` com motivo.

### 3. Preencher a spec
Edite os YAMLs. Formatos e exemplos: `references/escrever-regras.md`. Exemplo completo e válido: `references/exemplo/spec/`.
- `spec/spec.yaml`: visão, usuários, escopo e **fora de escopo**, domínios, não funcionais (`NF01`), premissas (`P01`), dúvidas, packs descartados.
- `spec/dominios/<nome>.yaml`: entidades, estados, permissões, regras, casos de uso, bordas.
- `spec/checklist.yaml`: responda cada item como `"sim PED-R03"` (cite a regra/NF/premissa que cobre), `"na: motivo"` ou `"?"`. Um item `sim` sem regra correspondente é lacuna: escreva a regra.
Domínio com mais de 15 regras deve ser dividido.

### 4. Portão
```
CHECK check
```
Corrija tudo que o script apontar e repita até `OK`. Não adicione conteúdo só para calar o script: se o item não se aplica, use `na:` com motivo real.

### 5. Revisão adversarial
Releia a spec como um revisor cético, seguindo `references/revisao.md` (lista de perguntas de lacunas e contradições). Corrija na spec, rode `CHECK check` de novo. Apresente ao usuário um resumo curto: domínios, premissas assumidas e dúvidas. Gere a visão legível com `CHECK report` (`spec/SPEC.md`).
Só avance quando o usuário aprovar ou quando ele tiver dito para seguir direto.

### 6. Plano
Crie `spec/tarefas.yaml` (fatias verticais pequenas, cada uma citando regras):
```yaml
T01: {titulo: "Criar tarefa com validação", regras: [TAR-R01, TAR-R02]}
```
`CHECK check --plan` exige que **toda regra esteja em alguma tarefa**.

### 7. Implementar
Uma tarefa por vez. Cite o ID da regra em comentário perto do código e no nome/descrição dos testes (`# TAR-R01`, `test_TAR_R01_titulo_vazio`). Teste cada regra, incluindo as bordas.

### 8. Verificação
```
CHECK trace --strict
```
Lista regras sem código, sem teste e IDs órfãos. Resolva todas. Se o comportamento real divergir da spec, **atualize a spec** (ou pergunte) e rode `CHECK check` de novo.

## Se o agente não consegue rodar comandos
Siga o mesmo fluxo manualmente: crie os arquivos a partir de `templates/`, responda os packs de `packs/*.yaml` direto no `checklist.yaml`, e confira à mão os limites (regra <= 140 caracteres, sem TODO, sem dúvidas abertas, toda regra com teste).

## Referências
- `references/escrever-regras.md`: como escrever regras boas e curtas.
- `references/revisao.md`: perguntas da revisão adversarial.
- `references/exemplo/spec/`: spec mínima que passa no portão.
- `packs/*.yaml`: checklists por tipo de app (leia só os que se aplicam).
