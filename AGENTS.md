## Protocolo spec-first (obrigatório)
Para app novo ou feature grande, NÃO escreva código antes de a spec passar no portão.
A skill está vendordada no projeto em `.devin/skills/spec-first/` — `CHECK` = `python .devin/skills/spec-first/scripts/check.py` (rode da raiz).
1. Leia `.devin/skills/spec-first/SKILL.md` e siga o fluxo (entrevista curta -> spec YAML em `spec/` -> revisão).
2. Portão: `CHECK check` deve terminar com OK. Para planejar: `CHECK check --plan`.
3. Ao codar, cite os IDs das regras (ex.: `PED-R01`) em comentários e nomes de testes.
4. No fim: `CHECK trace --strict`. Mudou comportamento? Atualize a spec primeiro.
Bugfix pequeno e ajuste visual não precisam de spec.
