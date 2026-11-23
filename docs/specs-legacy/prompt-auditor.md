### 📋 O "Prompt Ouro" para Auditorias de Código — Extensão Android

> **Pré-requisito:** use este arquivo em conjunto com `specs/prompt-auditor-base.md`, que contém o núcleo genérico (papel, vetores 1 e 4, metodologia exigida, metodologia de eficiência e formato de saída). Este arquivo só acrescenta o que é específico da plataforma Android: os vetores 2 e 3, e os arquivos-alvo típicos de varredura.

> **Vetores adicionais (específicos de Android), a somar aos vetores genéricos da base:**
> 2. **Armazenamento Seguro:** Vazamento de dados confidenciais (chaves, senhas, tokens) em SharedPreferences, Logs do sistema ou no banco de dados SQLite/Room sem criptografia (ex: falhas no KeyStore).
> 3. **Privacidade (Android Framework):** Uso indevido de permissões, `Exported Components` (Activities/Services abertos no Manifest) e escuta indevida de componentes (ex: Clipboard, Sensores).
>
> **Arquivos-alvo prioritários para a varredura profunda (complementa a Metodologia Exigida da base):**
> - `AndroidManifest.xml` (permissões, componentes `exported`, `usesCleartextTraffic`, `allowBackup`, `dataExtractionRules`/`backup_rules`)
> - `build.gradle`/`build.gradle.kts` (assinatura de release, minificação, dependências de criptografia)
> - Classes utilitárias de criptografia (ex: wrappers de `AndroidKeyStore`, `Cipher`, PBKDF2/AES)
> - Gerenciadores de servidores locais embutidos (FTP/HTTP/WebDAV/SFTP, sockets TCP/UDP de P2P/mesh)
> - Camada de persistência (`SharedPreferences`, `Room`/SQLite, `EncryptedSharedPreferences` — ou ausência dela)

> **Combine com a base assim:** ao montar o prompt final para a IA, cole o conteúdo de `prompt-auditor-base.md` e adicione este arquivo como complemento antes de enviar, ou referencie ambos os caminhos explicitamente na tarefa (`Use specs/prompt-auditor-base.md + specs/prompt-auditor.md como guia de auditoria`).

***

### 💡 Notas específicas desta extensão

- Os vetores 2 e 3 exigem conhecimento do framework Android (Manifest, Keystore, SharedPreferences, Room) que não se aplica a outras plataformas — por isso ficam isolados aqui em vez de na base.
- Ao reutilizar esta metodologia em outro projeto Android, este arquivo pode ser copiado sem alterações; apenas a base genérica (`prompt-auditor-base.md`) tende a evoluir de forma compartilhada entre projetos de qualquer stack.
- Para outras plataformas (iOS, Web/backend, Flutter, etc.), crie um `prompt-auditor-<plataforma>.md` análogo a este, substituindo os vetores 2/3 e os arquivos-alvo pelos equivalentes daquele ecossistema (ex.: iOS → Keychain, Info.plist, App Transport Security; Web → cookies/CSP/CORS, ORM/SQL, variáveis de ambiente).
