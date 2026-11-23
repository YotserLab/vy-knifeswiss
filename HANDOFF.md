# HANDOFF — VYKnifeSwiss

## Estado atual
- Versão: `1.1.57` (versionCode 165)
- Spec-first ativo: `spec/` com 13 domínios, 172 regras as-is; `check`, `check --plan` e `trace --strict` verdes; regra por domínio ≤ 15 (`MAX_RULES` do check.py).
- Suíte: `app/src/test/.../spectests/` cobre toda regra (JVM puro + contratos de fonte); `testDebugUnitTest` verde.
- Estrutura: `ui/screens/` por domínio (chat, docs, files, media, network, obd, productivity, security, system, deviceinfo, filemanager); `utils/crypto` + `utils/net`; `specs/` legado em `docs/specs-legacy/`; samples em `samples/`; scripts em `scripts/` e `windows/`.
- Build: `settings.gradle` Groovy é o arquivo efetivo (Gradle o prefere ao `.kts` quando ambos existem); módulo `mesh/` não entra no build — código mesh vive em `:app` (`com.example.mesh`).
- Repositório: `github.com/YotserLab/vy-knifeswiss`, público, branch `main`.
- Regras: bump de versão antes de compilar (`.devin/rules/versao-pump.md`); commits em Conventional Commits (inglês, ≤72 chars).
- Nota: `samples/sample_miniapp/miniapp_private.pem` é chave privada de EXEMPLO (assinatura de miniapps de demonstração) — aceito conscientemente.
