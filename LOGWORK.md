# LOGWORK — VYKnifeSwiss

## 2026-11-24 - Especificação Spec-First do domínio WebDrop
- Criada especificação do novo domínio `webdrop` (prefixo `WBD`, regras `WBD-R01` a `WBD-R11`) cobrindo:
  - Servidor HTTP autônomo na porta 8088 com bind no IP Wi-Fi local.
  - Autenticação por PIN aleatório de 4 dígitos (exibido na UI do app e codificado em QR code URL ou entrada manual no navegador).
  - Destino no armazenamento interno em `Download/WebDrop/`.
  - Compartilhamento bidirecional: drag-and-drop / seleção no navegador para upload no telefone, e seleção de arquivos locais no telefone para download na web.
  - Streaming direto em disco com limite de 2 GiB por arquivo.
- Atualizado `spec/spec.yaml` com o domínio `webdrop` e escopo atualizado.
- Atualizado `spec/checklist.yaml` e adicionada tarefa `T14` em `spec/tarefas.yaml`.
- Executado `check.py check --plan` (aprovado com OK) e `check.py report` (SPEC.md gerado).


## 2026-11-24 - Pentest fuzzing do InternalHttpFileServer + fixes
- Review com a skill `offensive-fuzzing` achou 3 problemas médios e 2 baixos no servidor HTTP de cast/chat.
- Criado `scripts/fuzz_http_server.py` (stdlib, 31 casos + modo `--slow`) e harness JVM `FuzzHarnessTest` (Robolectric, ativado por `/tmp/vy_fuzz_enable`) que sobe o server real no host para fuzz via 127.0.0.1.
- Confirmado no arame: métodos com prefixo (GETX, HEADER) executavam GET real; conexões dribladas prendiam threads sem teto.
- Fixes aplicados e revalidados pelo fuzzer:
  - M2: dispatch por método exato (GET/HEAD); métodos inventados agora recebem 405.
  - M1: Semaphore de 32 slots recusa conexão extra + deadline total de 30s no head (anti-slowloris).
  - M3: stream aberto antes dos headers — arquivo removido por cleanupExpired entre check e open retorna 404 em vez de truncar.
- Residual aceitável: requisição LF-only/CR-only segura thread até o deadline de 30s, agora limitado a 32 conexões.
