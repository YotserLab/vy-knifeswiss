---
name: Secure Android Coding
description: Aplica regras estritas de segurança (armazenamento seguro, network, IPC) durante o desenvolvimento de componentes Android, para prevenir introdução de vulnerabilidades de segurança e fragilidades.
---

# Diretrizes de Desenvolvimento Seguro Android (Secure Coding)

Quando solicitado a escrever ou modificar código Android (Java/Kotlin/XML) neste projeto, você **deve obrigatóriamente** obedecer aos seguintes princípios para garantir a segurança dos dados do usuário e evitar vulnerabilidades que reprovariam em uma auditoria:

## 1. Armazenamento Seguro
- **Nunca use `SharedPreferences` comum para dados sensíveis** (tokens, senhas, chaves, dados de autenticação). Use sempre `EncryptedSharedPreferences` da biblioteca AndroidX Security.
- **Bancos de Dados:** Para SQLite/Room, se houver dados confidenciais na tabela, utilize SQLCipher ou equivalentes para criptografia em repouso.
- **Arquivos Locais:** Se o app possuir um cofre de senhas ou armazenar arquivos confidenciais localmente, certifique-se de que a camada de persistência utiliza criptografia forte (ex: AES/GCM/NoPadding) via `AndroidKeyStore`.
- **Logs:** Nunca faça log (via `Log.d`, `Log.i`, etc.) de dados sensíveis ou informações de autenticação.

## 2. Comunicação de Rede Segura
- **Sempre exija TLS/SSL.** Nunca permita tráfego em texto plano (HTTP). Certifique-se de que a diretiva `usesCleartextTraffic="false"` seja mantida e respeitada.
- **Validação de Certificados:** Nunca implemente TrustManagers customizados inseguros que bypassam a validação de certificados SSL (ex: aceitando todos os certificados, ignorando erros de validação).
- **Tráfego P2P e Servidores Embutidos:** Se estiver lidando com serviços de rede locais (Servidor HTTP embutido, FTP, WebDAV, Mesh), certifique-se de que portas abertas sejam autenticadas e trafeguem sobre TLS, ou estejam restritas a cenários seguros validados.

## 3. Segurança de Componentes (IPC) e Privacidade
- **Componentes Exportados:** Por padrão, Activities, Services, BroadcastReceivers e ContentProviders devem ter `android:exported="false"`. Se precisarem ser exportados, justifique no código e proteja-os com permissões fortes.
- **Path Traversal e FileProviders:** Ao servir ou gerenciar arquivos, implemente verificações estritas contra _Path Traversal_ (ex: `../`). Garanta que os usuários não possam escapar da raiz de dados delimitada para o serviço.
- **Sensibilidade de Sensores/Clipboard:** Não leia a área de transferência indiscriminadamente. Não habilite `allowBackup="true"` sem considerar o vazamento de dados locais para o backup da nuvem do Google (use regras explícitas se habilitado).

## 4. Evitando Mocks e Funcionalidades Frágeis
- Não implemente fluxos de segurança (como autenticação TOTP ou verificação de chaves) usando dados gerados aleatoriamente (`random()`) ou lógicas incompletas. Se uma feature de segurança for solicitada, escreva a implementação real usando as bibliotecas corretas (Android Keystore, criptografia real, validação hash real).

**Verificação Proativa:** Ao gerar qualquer código, verifique mentalmente se você está aderindo a este checklist. Sempre escolha a alternativa segura _by default_.
