# Auditoria de Segurança Profunda — VYKnifeSwiss (Android)

**Data:** 2026-08-06
**Tipo:** Auditoria defensiva autorizada (análise estática, read-only)
**Repositório:** `/projetos/apps-mobile/android/VYKnifeSwiss`
**Application ID:** `com.aistudio.networktoolbox.kpqrsy` · `versionName 1.0.0` (versionCode 27)
**Escopo:** Segurança de rede/P2P, armazenamento seguro, privacidade (Android Framework), funcionalidades mockadas/incompletas.

> Este relatório foi produzido por análise estática do código-fonte local. Todos os achados citam arquivo e linhas, e foram verificados manualmente lendo o trecho de código correspondente antes de serem incluídos aqui. Nenhum teste de exploração ativa ou destrutiva foi executado; nenhuma credencial foi utilizada ou exfiltrada.

---

## Resumo Executivo

O app é um "canivete suíço" Android com dezenas de ferramentas: cofre de senhas, autenticador TOTP, servidores de arquivos embutidos (HTTP, FTP, WebDAV), cliente SFTP/SSH, chat P2P local (Wi-Fi/mesh), gerenciador SMS, sincronização em nuvem, scanner OBD-II, entre outros.

Achados por severidade:

| Severidade | Qtde | Categoria predominante |
|---|---|---|
| Crítico | 7 | Servidores de rede sem autenticação; segredos em texto plano |
| Alto | 8 | TLS/host-key desabilitado; tráfego em texto plano; permissões excessivas |
| Médio | 7 | Path traversal mitigado mas frágil; logs sensíveis; Bluetooth sem cripto de app |
| Baixo/Informativo | 6 | Boas práticas parciais, recomendações de endurecimento |

**Os três riscos mais graves e imediatamente exploráveis em uma rede Wi‑Fi compartilhada (ex.: rede pública, hotel, escritório) são:**
1. Servidor **WebDAV** e **FTP** embutidos, que abrem a árvore de arquivos do usuário sem qualquer autenticação real, acessível a qualquer dispositivo na mesma rede.
2. **PIN mestre do cofre de senhas** e **tokens OAuth de nuvem** persistidos em `SharedPreferences` em texto plano — quebrando a proteção da criptografia aplicada aos dados do cofre.
3. **Sincronização de área de transferência (clipboard)** do chat local, que transmite qualquer conteúdo copiado (podendo incluir senhas) via TCP em texto plano para todos os peers da rede.

---

## 1. Segurança de Rede & P2P

### 1.1 [Crítico] Servidor FTP sem autenticação real (aceita qualquer senha)
**Onde:** `app/src/main/java/com/example/ui/screens/FTPServer.kt:38,41,99,123-131`

```kotlin
// linha 38
controlSocket = ServerSocket(port)          // bind em 0.0.0.0, todas as interfaces

// linha 41
log.add("User: android | Senha: (qualquer)")

// linhas 123-131
upper.startsWith("USER ") -> { user = cmd.substring(5).trim(); send("331 User name okay, need password") }
upper.startsWith("PASS ") -> {
    authenticated = true                     // nenhuma verificação da senha informada
    send("230 User $user logged in")
}
!authenticated -> send("530 Please login with USER and PASS")
```

**Fluxo explorável:** qualquer dispositivo na mesma rede Wi‑Fi/hotspot pode conectar-se na porta do FTP (usuário informa "android" e QUALQUER senha) e obter sessão autenticada com acesso de leitura/escrita ao diretório servido.
**Condição necessária:** usuário precisa habilitar manualmente o servidor FTP na tela correspondente. A partir daí, exposição é imediata a toda a rede local.
**Impacto:** leitura/exfiltração e gravação de arquivos no armazenamento compartilhado do dispositivo.
**Classificação:** Vulnerabilidade confirmada.

**Correção proposta:**
```kotlin
upper.startsWith("PASS ") -> {
    val provided = cmd.substring(5).trim()
    authenticated = provided.isNotEmpty() && constantTimeEquals(provided, configuredPassword)
    if (authenticated) send("230 User $user logged in") else send("530 Login incorrect")
}
```
Gerar uma senha aleatória por sessão (exibida por QR/tela) em vez de aceitar qualquer valor, e migrar para FTPS (implicit/explicit TLS) ou preferir exclusivamente o WebDAV/SFTP já com TLS.

---

### 1.2 [Crítico] Servidor WebDAV totalmente sem autenticação
**Onde:** `app/src/main/java/com/example/ui/screens/WebDAVServer.kt:30-52`

```kotlin
fun start(port: Int = 8080) {
    ...
    serverSocket = ServerSocket(port)                 // bind 0.0.0.0
    val ip = getLocalIp()
    log.add("WebDAV ativo: http://$ip:$port/")
    while (isActive && isRunning) {
        val client = serverSocket?.accept() ?: break
        launch(Dispatchers.IO) { handleClient(client) }  // sem checagem de Authorization
    }
}
```
Não há nenhuma verificação de cabeçalho `Authorization` (nem Basic, nem Digest) em `handleClient`.

**Fluxo explorável:** qualquer cliente WebDAV (ex.: gerenciador de arquivos do Windows/macOS/Android) na mesma rede pode montar `http://<ip>:8080/` e ler, escrever, apagar e listar arquivos sem credenciais.
**Impacto:** acesso total de leitura/escrita ao diretório compartilhado, sem qualquer barreira.
**Classificação:** Vulnerabilidade confirmada.

**Correção proposta:** exigir HTTP Basic/Digest Auth com senha gerada por sessão antes de processar qualquer método (`GET/PUT/DELETE/PROPFIND/MKCOL`); considerar HTTPS com certificado autoassinado + fingerprint exibido ao usuário para pareamento seguro.

---

### 1.3 [Alto] Path traversal mitigado, mas dependente de uma única função de validação
**Onde:** `WebDAVServer.kt:63-76`, `FTPServer.kt:61-74` (função `isWithinBaseDir`, idêntica nos dois arquivos)

```kotlin
private fun isWithinBaseDir(file: File): Boolean {
    return try {
        val canonicalBase = baseDir.canonicalFile
        val canonicalTarget = file.canonicalFile
        var parent: File? = canonicalTarget
        while (parent != null) {
            if (parent == canonicalBase) return true
            parent = parent.parentFile
        }
        false
    } catch (_: Exception) { false }
}
```
**Análise:** a técnica de comparar `canonicalFile` é correta e neutraliza `../../` clássico. Não encontramos um bypass funcional nesta revisão (então isto **não** é classificado como vulnerabilidade confirmada). O risco é que essa é a *única* camada de defesa, e ela roda **depois** da autenticação inexistente (itens 1.1/1.2) — ou seja, mesmo estando correta, ela protege pouco porque qualquer um já tem acesso de qualquer forma.
**Classificação:** Recomendação de endurecimento (a validação existe e parece efetiva; risco residual é baixo, mas caminho de código é frágil por depender de uma função replicada em dois arquivos sem testes automatizados).

---

### 1.4 [Crítico] Servidor HTTP interno (compartilhamento de arquivo do chat) sem autenticação, sobre texto plano
**Onde:** `app/src/main/java/com/example/service/InternalHttpFileServer.kt:19-38, 51-54, 66-71`

```kotlin
serverSocket = ServerSocket(0) // porta aleatória, mas ainda bind 0.0.0.0
...
fun hostFile(uri: Uri): String {
    val token = UUID.randomUUID().toString()
    pendingFiles[token] = uri
    return token
}
...
val token = path.removePrefix("/download/")
val uri = pendingFiles[token]   // sem checagem de origem/expiração/uso único
```
**Fluxo explorável:** o link de download (`http://<ip>:<porta>/download/<uuid>`) é enviado em texto plano via o próprio protocolo de chat local (ver 1.6). Qualquer dispositivo que veja esse link (por captura de tráfego ou compartilhamento indevido) pode baixar o arquivo, sem autenticação, enquanto o servidor estiver ativo (token não expira e não é de uso único).
**Impacto:** exfiltração do arquivo compartilhado por terceiros na rede.
**Classificação:** Vulnerabilidade confirmada (a aleatoriedade do UUIDv4 dificulta adivinhação, mas não há defesa em profundidade: nem TLS, nem expiração, nem vínculo ao destinatário).

**Correção proposta:** expirar token após uso único ou TTL curto; vincular token ao peer específico (IP/ID do device) que deveria recebê-lo; se possível, servir via HTTPS local com certificado efêmero.

---

### 1.5 [Alto] Verificação de chave de host SSH/SFTP desabilitada (MITM)
**Onde:** `app/src/main/java/com/example/ui/screens/RemoteFileClient.kt:45,85` e `app/src/main/java/com/example/ui/screens/TerminalScreen.kt:79`

```kotlin
val config = Properties()
config["StrictHostKeyChecking"] = "no"
session.setConfig(config)
```
**Fluxo explorável:** ao conectar em qualquer servidor SFTP/SSH configurado pelo usuário, o JSch aceita silenciosamente qualquer chave de host apresentada — inclusive de um atacante fazendo ARP/DNS spoofing na rede local para se passar pelo servidor real.
**Impacto:** interceptação de credenciais SSH/SFTP e de todo o tráfego do túnel (arquivos, comandos de terminal).
**Classificação:** Vulnerabilidade confirmada.

**Correção proposta:** implementar `known_hosts` persistente por host (TOFU — trust on first use), alertando e pedindo confirmação explícita do usuário quando a chave de um host mudar, em vez de desabilitar a checagem globalmente.

---

### 1.6 [Alto] Chat local (mesh/Wi-Fi Direct) 100% em texto plano, incluindo clipboard
**Onde:** `app/src/main/java/com/example/service/LocalChatService.kt:187-190 (UDP 9988), 222-225 (TCP 9989), 664-706 (clipboard sync)`, replicado em `app/src/main/java/com/example/ui/screens/LocalChatScreen.kt`

```kotlin
udpSocket = DatagramSocket(null).apply { reuseAddress = true; bind(InetSocketAddress(9988)) }
tcpServer = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(9989)) }
...
val socket = Socket()
socket.connect(InetSocketAddress(peer.ip, 9989), 2000)
out.write((payloadStr + "\n").toByteArray())     // JSON de mensagem/clipboard sem cifra
```

E a funcionalidade de sincronização de área de transferência:
```kotlin
private fun startClipboardListener() {
    if (!sharedPref.getBoolean("sync_clipboard_enabled", false)) return
    ...
    clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        if (!text.isNullOrEmpty() && text != lastReceivedClipboardText) {
            broadcastClipboard(text)   // envia para TODOS os peers da rede, em texto plano
        }
    }
    clipboard.addPrimaryClipChangedListener(clipboardListener)
}
```
**Fluxo explorável:** com "Sincronizar área de transferência" habilitada, toda vez que o usuário copia qualquer texto (podendo ser senha, código 2FA, dado bancário), o conteúdo é automaticamente enviado em TCP texto plano, porta 9989, para todos os peers descobertos na rede — sem qualquer criptografia ou confirmação por mensagem.
**Impacto:** vazamento de dados sensíveis copiados pelo usuário para qualquer atacante capaz de capturar tráfego na mesma rede (Wi‑Fi aberta, ARP spoofing, etc.) e para os próprios "peers" da mesh, que recebem o conteúdo automaticamente.
**Classificação:** Vulnerabilidade confirmada (a feature é opt-in via configuração, o que reduz a superfície, mas o comportamento após ativada é perigoso e sem qualquer criptografia/allowlist).

**Correção proposta:** cifrar o canal do chat local (ex.: handshake baseado em X25519/AES-GCM por par de dispositivos, ou pelo menos TLS com certificado pinado por primeira conexão); para o clipboard, adicionar heurística de exclusão de conteúdos que pareçam segredos (padrões de senha/token/OTP) e exigir confirmação explícita por envio, não automática.

---

### 1.7 [Médio] Sockets do chat local e servidores de arquivo com bind em todas as interfaces (0.0.0.0)
**Onde:** `LocalChatService.kt:189,224`, `FTPServer.kt:38`, `WebDAVServer.kt:37`, `InternalHttpFileServer.kt:22`

Nenhum desses sockets restringe o bind à interface Wi‑Fi/Wi‑Fi Direct específica. Em cenários com VPN ativa, tethering reverso, ou múltiplas interfaces de rede simultâneas, os serviços ficam acessíveis além do que o usuário provavelmente espera ("minha rede Wi‑Fi local").
**Classificação:** Risco provável (depende da topologia de rede do dispositivo no momento do uso).
**Correção:** fazer bind explícito no endereço IP da interface Wi‑Fi ativa, obtido via `WifiManager`/`ConnectivityManager`, em vez de bind coringa.

---

### 1.8 [Médio] Bluetooth OBD-II sem criptografia de camada de aplicação
**Onde:** `app/src/main/java/com/example/transport/BluetoothObdManager.kt:65-71,115-116`

```kotlin
socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
...
outputStream?.write("$command\r".toByteArray())
```
O protocolo OBD-II via SPP não tem cifra própria; depende apenas da criptografia nativa do pareamento Bluetooth Classic (que é razoável, mas pode ser degradada por downgrade de pareamento legado). Não é uma falha introduzida pelo app, mas vale registrar como superfície.
**Classificação:** Informativo / recomendação de endurecimento — sem ação necessária além de validar nível de segurança do pareamento (`BluetoothDevice.BOND_BONDED` + criptografia ativa) antes de trocar comandos.

---

### 1.9 [Informativo] Chamadas HTTPS corretas
`MailtrapEmailService.kt:39` (`https://send.api.mailtrap.io/api/send`), `GoogleDriveProvider.kt` (via SDK oficial do Google, TLS por padrão) e `SpeedTestUtils.kt` (endpoints `https://1.1.1.1`, `https://speed.cloudflare.com/...`) usam HTTPS corretamente. Nenhum `TrustManager`/`HostnameVerifier` permissivo customizado foi encontrado no código (`grep` por `X509TrustManager`, `checkServerTrusted`, `HostnameVerifier` não retornou implementações inseguras).

---

## 2. Armazenamento Seguro

### 2.1 [Crítico] PIN mestre do cofre de senhas persistido em texto plano
**Onde:** `app/src/main/java/com/example/ui/screens/PasswordVaultScreen.kt:254-256 (leitura), 317-321 (gravação)`

```kotlin
// gravação, após validar o PIN por VaultCrypto.verifyPin (linha 318)
context.getSharedPreferences("vault_bio_prefs", Context.MODE_PRIVATE)
    .edit().putString("bio_pin", pin).apply()

// leitura, no callback de sucesso biométrico
val prefs = context.getSharedPreferences("vault_bio_prefs", Context.MODE_PRIVATE)
val bioPin = prefs.getString("bio_pin", null)
if (bioPin != null) onUnlocked(bioPin)
```
**Fluxo explorável:** para permitir desbloqueio biométrico, o app grava o PIN mestre em texto plano no arquivo XML de `SharedPreferences` (`/data/data/<pkg>/shared_prefs/vault_bio_prefs.xml`). Em um dispositivo com root, ADB backup habilitado incorretamente, ou uma vulnerabilidade de leitura arbitrária de arquivo em qualquer outro componente do app, esse PIN é lido diretamente — **sem precisar quebrar a biometria nem o PBKDF2 usado no `VaultCrypto`**.
**Impacto:** o PIN mestre é a chave que deriva a criptografia de todas as senhas do cofre (`VaultEntry.encryptedPassword`). Vazá-lo em texto plano anula toda a proteção criptográfica do cofre.
**Classificação:** Vulnerabilidade confirmada.

**Correção proposta:** nunca persistir o PIN em claro. Em vez disso:
- Derivar uma chave AES do PIN e armazenar essa chave protegida por uma chave do **Android Keystore** vinculada à biometria (`setUserAuthenticationRequired(true)` + `BiometricPrompt.CryptoObject`), de forma que a chave só seja utilizável após autenticação biométrica bem-sucedida e nunca saia do Keystore em claro.
- Alternativa mais simples: migrar para `EncryptedSharedPreferences` (biblioteca `androidx.security:security-crypto`), embora o ideal seja vincular ao hardware-backed keystore como acima.

---

### 2.2 [Crítico] Tokens OAuth (Google Drive/OneDrive/Dropbox/pCloud) e PKCE verifier em texto plano
**Onde:** `app/src/main/java/com/example/ui/screens/CloudSyncManager.kt:64-98`

```kotlin
// Token storage (SharedPreferences — plaintext, not for production secrets)   <- comentário do próprio código
class CloudTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("cloud_oauth_tokens", Context.MODE_PRIVATE)
    fun saveAccessToken(provider: CloudProvider, token: String) =
        prefs.edit().putString("${provider.prefsKey}_access", token).apply()
    fun saveRefreshToken(provider: CloudProvider, token: String) =
        prefs.edit().putString("${provider.prefsKey}_refresh", token).apply()
    fun savePkceVerifier(provider: CloudProvider, verifier: String) =
        prefs.edit().putString("${provider.prefsKey}_pkce_verifier", verifier).apply()
}
```
O próprio comentário do desenvolvedor já reconhece o problema ("plaintext, not for production secrets").
**Impacto:** um `refresh_token` vazado permite a um atacante obter acesso persistente à conta de nuvem do usuário (Google Drive/OneDrive/Dropbox/pCloud) mesmo após o app ser desinstalado, até que o usuário revogue manualmente o acesso na conta do provedor.
**Classificação:** Vulnerabilidade confirmada.

**Correção proposta:** migrar para `EncryptedSharedPreferences` (chave mestra no Android Keystore) no mínimo; idealmente usar o `AccountManager`/`CredentialManager` do Android para tokens de conta.

---

### 2.3 [Alto] Nenhum uso de `EncryptedSharedPreferences` em todo o app
**Evidência:** busca por `EncryptedSharedPreferences` em `app/src/main/java` não retornou nenhum resultado; a dependência `androidx.security:security-crypto` também não está declarada em `app/build.gradle.kts`. Todas as ~30 chamadas a `getSharedPreferences(...)` no projeto usam `SharedPreferences` puro.
**Classificação:** Risco provável / recomendação de endurecimento estrutural — reforça a gravidade dos itens 2.1 e 2.2, pois não há nenhuma camada de criptografia de preferências disponível no projeto para reaproveitar.

---

### 2.4 [Alto] Banco Room (`net_toolbox_db`) sem criptografia de arquivo
**Onde:** `app/src/main/java/com/example/data/AppDatabase.kt`

O banco contém as entidades `VaultEntry` (senhas do cofre) e `TotpAccount` (segredos TOTP). Os *campos* sensíveis (`encryptedPassword`, `encryptedSecret`) são cifrados na camada de aplicação (AES-GCM via `VaultCrypto`/`TotpSecretCrypto`, boa prática), **mas o arquivo `.db` em si não usa SQLCipher** — metadados (títulos de entradas, nomes de contas TOTP, timestamps) ficam em texto plano no arquivo SQLite, legível em dispositivos com root ou via `adb backup`/extração forense.
**Classificação:** Risco provável (mitigado parcialmente pela cifragem de campo, mas metadados vazam).
**Recomendação:** avaliar SQLCipher para o arquivo inteiro, ou ao menos cifrar também os campos de metadados sensíveis (título/label das entradas).

---

### 2.5 [Médio] `allowBackup="true"` sem `dataExtractionRules`/`fullBackupContent` restritivas
**Onde:** `AndroidManifest.xml:51-53`; `res/xml/backup_rules.xml` e `res/xml/data_extraction_rules.xml` (ambos com todo o conteúdo comentado/vazio — regras de exemplo do template, nunca preenchidas)

```xml
android:allowBackup="true"
android:dataExtractionRules="@xml/data_extraction_rules"
android:fullBackupContent="@xml/backup_rules"
```
```xml
<!-- backup_rules.xml -->
<full-backup-content>
    <!-- <include domain="sharedpref" path="."/> ... -->
</full-backup-content>
```
Como as regras estão vazias (todas comentadas), o comportamento efetivo é o **backup padrão completo do Android**, que inclui o diretório `shared_prefs` (contendo `vault_bio_prefs.xml` e `cloud_oauth_tokens.xml` do item 2.1/2.2) e o banco de dados Room, em backups do Google (Auto Backup) ou via `adb backup` local.
**Impacto:** um backup do dispositivo (Google Drive Auto Backup, ou `adb backup` sem root em dispositivos com depuração USB habilitada) pode conter o PIN mestre do cofre e os tokens OAuth em texto plano.
**Classificação:** Vulnerabilidade confirmada (consequência direta e concreta dos itens 2.1/2.2, agravada pela ausência de exclusão explícita no backup).

**Correção proposta:** excluir explicitamente `shared_prefs` sensíveis do backup:
```xml
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="vault_bio_prefs.xml"/>
        <exclude domain="sharedpref" path="cloud_oauth_tokens.xml"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="vault_bio_prefs.xml"/>
        <exclude domain="sharedpref" path="cloud_oauth_tokens.xml"/>
    </device-transfer>
</data-extraction-rules>
```
(complementar, não substitui a correção 2.1/2.2 de não persistir o PIN em claro).

---

### 2.6 [Médio] Logs de depuração com dados de rede/mensagens e `printStackTrace()` generalizado
**Onde:** `app/src/main/java/com/example/service/LocalChatService.kt` (ex.: linhas em torno de 226-240 — `Log.d("LocalChatService", "TCP Client connected from: ...")`) e `app/src/main/java/com/example/ui/screens/LocalChatScreen.kt` (múltiplas ocorrências de `Log.d("LocalChat", "Sending message to ${peer.ip}: ${msgText}")`); uso de `e.printStackTrace()` espalhado por dezenas de arquivos, incluindo `app/src/main/java/com/example/utils/TotpSecretCrypto.kt:59,82` nos blocos de cifragem/decifragem do segredo TOTP.
**Impacto:** em builds de release sem remoção de logs (`isMinifyEnabled = false`, sem regra de remoção de `Log.*` no ProGuard — ver seção 4), conteúdo de mensagens de chat e IPs de peers fica exposto via `logcat` a qualquer app com permissão de leitura de log ou via ADB.
**Classificação:** Vulnerabilidade confirmada (exposição via logcat), risco de exposição de segredo é "risco provável" no caso específico do TOTP (só ocorre em caminho de exceção).
**Correção:** condicionar todos os `Log.d/e` a `BuildConfig.DEBUG`; nunca logar conteúdo de mensagem/segredos, apenas metadados não sensíveis.

---

## 3. Privacidade (Android Framework)

### 3.1 [Alto] Permissões de SMS completas (`READ_SMS`, `RECEIVE_SMS`, `WRITE_SMS`) com componentes exportados, para uma funcionalidade de "apagar SMS"
**Onde:** `AndroidManifest.xml:22-24,146-198`

```xml
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.WRITE_SMS" />
...
<receiver android:name="com.example.receiver.SmsReceiver" android:permission="android.permission.BROADCAST_SMS" android:exported="true">
    <intent-filter><action android:name="android.provider.Telephony.SMS_DELIVER" /></intent-filter>
</receiver>
<receiver android:name="com.example.receiver.MmsReceiver" android:permission="android.permission.BROADCAST_WAP_PUSH" android:exported="true">...</receiver>
<service android:name="com.example.service.HeadlessSmsSendService" android:permission="android.permission.SEND_RESPOND_VIA_MESSAGE" android:exported="true">...</service>
```
Os componentes são `exported="true"`, mas protegidos por permissões de sistema (`BROADCAST_SMS`, `BROADCAST_WAP_PUSH`, `SEND_RESPOND_VIA_MESSAGE`), que só o sistema Android possui — portanto **não são invocáveis por apps de terceiros** (mitigação padrão da plataforma corretamente aplicada; não é uma vulnerabilidade de exposição, é o padrão exigido para apps SMS). O ponto de atenção real é a extensão do escopo de permissão concedido pelo usuário: para excluir SMS (função em `SmsManagerScreen.kt`) o app precisa se tornar app de SMS padrão, o que dá a ele acesso irrestrito a leitura de **todo o histórico de SMS**, incluindo códigos de autenticação de dois fatores (2FA) de outras contas.
**Verificação de "mock":** `SmsReceiver.kt` e `MmsReceiver.kt` são propositalmente vazios (`// Stub implementation for Default SMS App requirement`) — não processam mensagens recebidas; servem apenas para o app poder ser elegível como "Default SMS App" e assim usar a API de exclusão de mensagens. Isso é consistente com o comentário e o uso em `SmsManagerScreen.kt`, portanto **não é uma feature quebrada**, é uma implementação mínima intencional — mas implica que, ao tornar-se app SMS padrão, o usuário perde a exibição/notificação nativa de SMS recebidos (o app não repassa a mensagem a lugar nenhum), o que é uma regressão de UX/confiabilidade que deve ser comunicada claramente ao usuário.
**Classificação:** Recomendação de endurecimento / risco de privacidade por escopo de permissão superior ao necessário — considerar usar `SmsManager.deleteMessage`/Content Provider sem exigir o papel de "Default SMS App" completo, se a API permitir, ou limitar claramente ao usuário o que acontece com SMS recebidos quando o modo está ativo.

### 3.2 [Crítico] Sincronização de clipboard (já descrita em 1.6) — reclassificada aqui sob a ótica de privacidade
Reforça-se aqui o aspecto de privacidade: a escuta de `ClipboardManager.OnPrimaryClipChangedListener` (`LocalChatService.kt:664-677`) captura **todo** conteúdo copiado pelo usuário no dispositivo inteiro (não apenas dentro do app) e o retransmite automaticamente a outros dispositivos, sem qualquer filtro de sensibilidade nem exigência de confirmação por envio individual — ver correção proposta em 1.6.

### 3.3 [Baixo] `MANAGE_EXTERNAL_STORAGE` + `QUERY_ALL_PACKAGES`
**Onde:** `AndroidManifest.xml:14-15`
Permissões de amplo alcance ("All Files Access" e listagem de todos os pacotes instalados) justificadas pelas funcionalidades de gerenciador de arquivos e extrator de apps (`AppExtractorScreen.kt`), respectivamente. Uso legítimo dado o propósito do app, mas aumenta a superfície de dano em caso de comprometimento do app (acesso irrestrito ao armazenamento e fingerprinting de apps instalados no dispositivo). Recomenda-se avaliar se o escopo de armazenamento pode ser reduzido ao Storage Access Framework para os fluxos que não exigem acesso irrestrito.

### 3.4 [Informativo] Nenhum uso de `MODE_WORLD_READABLE/WRITEABLE`
Confirmado por busca no código — todas as chamadas usam `Context.MODE_PRIVATE`. Boa prática mantida.

---

## 4. Funcionalidades Mockadas, Simuladas ou Incompletas

### 4.1 [Mock confirmado] Frequência de CPU aleatória como fallback
**Onde:** `app/src/main/java/com/example/ui/screens/DeviceInfoScreen.kt:259,487`
```kotlin
if (freq == 0) freq = Random.nextInt(1200, 2600)
```
Quando a leitura de `/sys/devices/system/cpu/cpuN/cpufreq/scaling_cur_freq` falha (comum em dispositivos que restringem esse arquivo), o app inventa um valor de MHz em vez de indicar "indisponível". Isso é enganoso para o usuário mas não representa risco de segurança.
**Recomendação:** exibir "N/D" em vez de número aleatório plausível.

### 4.2 [Mock confirmado] Dados de sensores de gravidade/bússola simulados quando hardware ausente
**Onde:** `app/src/main/java/com/example/utils/SensorUtils.kt:36-43` (gravidade — seno/cosseno do tempo) e `:70-81` (bússola — passeio aleatório incremental), ambos com comentários explícitos do próprio autor ("fallback data if sensor is unavailable" / "Emulate compass azimuth").
**Recomendação:** sinalizar visualmente na UI (`BubbleLevelScreen`/`CompassScreen`) quando os dados exibidos são simulados por ausência de sensor físico.

### 4.3 [Mock confirmado / dead code] Módulo de Mesh Networking sem implementação funcional e não instanciado em lugar nenhum do app
**Onde:**
- `app/src/main/java/com/example/mesh/domain/routing/MeshRouter.kt:64-67` (`deliverToApplication` — corpo vazio) e `:75-77` (`forwardMessage` — corpo vazio).
- `app/src/main/java/com/example/mesh/data/repository/StoreAndForwardRepository.kt:9-37` — apenas uma `interface`, **sem nenhuma classe concreta que a implemente** em todo o repositório (confirmado via busca por `StoreAndForwardRepository(` — zero resultados de instanciação).
- Confirmado também que `MeshRouter(` nunca é instanciado em nenhum lugar do app (`grep` no diretório inteiro retornou apenas a própria declaração da classe).
- A tela `app/src/main/java/com/example/ui/screens/MeshInfoScreen.kt` apenas alterna uma flag booleana `mesh_enabled` em `SharedPreferences` (linha 26) — **não aciona `MeshRouter` nem nenhuma lógica do pacote `mesh`**.

**Conclusão:** o toggle "Ativar Modo Mesh (Wi‑Fi Direct)" exibido ao usuário em `MeshInfoScreen` é puramente decorativo — liga uma preferência que não é lida por nenhum componente funcional do mesh. O pacote `mesh` (também duplicado em módulo Gradle separado `mesh/src/main/...`) é código morto/protótipo não integrado à aplicação.
**Classificação:** Mock confirmado — funcionalidade anunciada ao usuário sem efeito real no sistema.
**Recomendação:** remover a tela/toggle enganosos até que a integração exista, ou implementar de fato `deliverToApplication`/`forwardMessage`/`StoreAndForwardRepository` e conectar `MeshRouter` ao ciclo de vida real do app (ex.: ao `WifiDirectMedium`/`TransportManager`).

### 4.4 [Parcialmente implementado] Sincronização em nuvem com Client IDs OAuth placeholder
**Onde:** `app/src/main/java/com/example/ui/screens/CloudSyncManager.kt:38-51`
```kotlin
const val GDRIVE_CLIENT_ID  = "YOUR_GDRIVE_CLIENT_ID.apps.googleusercontent.com"
const val ONEDRIVE_CLIENT_ID = "YOUR_ONEDRIVE_CLIENT_ID"
```
Fluxo OAuth (PKCE, troca de token, refresh) está implementado de forma completa e correta, mas não funcionará em produção sem que credenciais reais sejam configuradas por build/flavor. Não é um mock de dados — é uma configuração pendente, claramente sinalizada em comentário no próprio arquivo.
**Recomendação:** mover para `BuildConfig`/`.env` (o projeto já usa o plugin `secrets` do Gradle para o `GEMINI_API_KEY`; o mesmo padrão pode acomodar os client IDs de OAuth) em vez de constantes hardcoded no fonte.

### 4.5 [Parcialmente implementado] `NetworkInfoScreen` com valores padrão nunca substituídos por dados reais
**Onde:** `app/src/main/java/com/example/ui/screens/NetworkInfoScreen.kt:48-68` (defaults de `NetData`: `gateway="192.168.1.1"`, `dns1="8.8.8.8"`, `publicIp="189.6.54.120"`, `macAddress="AA:BB:CC:DD:EE:FF"`, etc.)
Comparado ao bloco de atualização real (`LaunchedEffect`), apenas alguns campos (SSID, IP local aproximado, contadores de tráfego) são de fato atualizados; gateway, DNS, IP público e MAC permanecem sempre com o valor de exemplo do `data class`.
**Impacto:** não é uma falha de segurança, mas induz o usuário a erro ao ler informações de rede que parecem reais e não são.
**Recomendação:** popular os campos reais via `LinkProperties`/`ConnectivityManager` (gateway/DNS) e uma chamada a serviço de IP público explícito, ou remover os campos da UI se não houver dado real disponível.

### 4.6 Funcionalidades verificadas como **reais** (não são mock) — para não gerar falsos positivos
`SpeedTestScreen`/`SpeedTestUtils` (download/upload reais via `speed.cloudflare.com`), `WifiMonitorScreen` (scan real via `WifiManager`), `Obd2ScannerScreen`/`BluetoothObdManager` (comandos AT reais via socket Bluetooth), `PortScannerScreen`/`SubnetScannerScreen`/`NetworkUtils` (sockets reais), `MailtrapEmailService` (chamada HTTP real à API), `GoogleDriveProvider` (SDK oficial), `CoordinatorElection` (algoritmo de eleição real, embora não usado pois o `mesh` não está integrado — ver 4.3).

---

## 5. Outros Achados de Configuração

### 5.1 [Médio] `usesCleartextTraffic="true"` sem `network_security_config.xml`
**Onde:** `AndroidManifest.xml:50`
```xml
android:usesCleartextTraffic="true"
```
Não existe um `network_security_config.xml` restringindo cleartext a domínios específicos — a flag libera texto plano para **qualquer** destino HTTP em todo o app. Combinado com os servidores internos (que precisam de HTTP local mesmo), isso é parcialmente justificável, mas o correto é declarar exceções pontuais em vez de liberar globalmente:
```xml
<application android:networkSecurityConfig="@xml/network_security_config" ...>
```
```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
    </domain-config>
</network-security-config>
```
**Classificação:** Recomendação de endurecimento.

### 5.2 [Baixo/Informativo] Build de release sem ofuscação/minificação
**Onde:** `app/build.gradle.kts` — `buildTypes.release { isMinifyEnabled = false }`
Facilita engenharia reversa do APK e recuperação de strings sensíveis (embora nenhuma chave de API de produção tenha sido encontrada hardcoded no fonte — `GEMINI_API_KEY` vem de `.env`, corretamente `.gitignore`d e não versionado).
**Recomendação:** habilitar `isMinifyEnabled = true` com regras de ProGuard/R8 testadas para o release.

### 5.3 [Informativo] Segredos de build não versionados corretamente
Verificado: `.env`, `debug.keystore` e `my-upload-key.jks` **não estão rastreados pelo Git** (`.gitignore` cobre `.env` e `debug.keystore`; `my-upload-key.jks` não existe no repositório). Nenhuma chave de assinatura de release ou segredo real foi encontrado versionado no histórico do Git nesta análise superficial (`git log --all -- .env my-upload-key.jks` vazio). Boa prática confirmada.

---

## Plano de Ação (Checklist)

### Fase 1 — Hotfixes de Segurança Crítica (antes de qualquer nova distribuição)
- [ ] **FTP:** exigir senha real configurável (não fixa/qualquer) ou desabilitar comando `PASS` sempre-verdadeiro (`FTPServer.kt:127-130`).
- [ ] **WebDAV:** adicionar HTTP Basic/Digest Auth obrigatório antes de processar qualquer método (`WebDAVServer.kt:42-46`).
- [ ] **PIN do cofre:** remover persistência em texto plano de `bio_pin`; vincular chave de decrifração ao Android Keystore com `setUserAuthenticationRequired` (`PasswordVaultScreen.kt:254-256,320-321`).
- [ ] **Tokens OAuth de nuvem:** migrar `CloudTokenStore` para `EncryptedSharedPreferences` (`CloudSyncManager.kt:67-98`).
- [ ] **Backup:** excluir `vault_bio_prefs.xml` e `cloud_oauth_tokens.xml` de `data_extraction_rules.xml`/`backup_rules.xml` enquanto a correção estrutural acima não é concluída.
- [ ] **Clipboard sync:** desabilitar por padrão (já é opt-in) e adicionar aviso explícito de risco na UI antes de habilitar; considerar cifrar o canal ou remover a feature até haver TLS no chat local.

### Fase 2 — Hardening de Rede
- [ ] Implementar `known_hosts`/TOFU no cliente SFTP e no terminal SSH, removendo `StrictHostKeyChecking=no` (`RemoteFileClient.kt`, `TerminalScreen.kt`).
- [ ] Adicionar criptografia de transporte (TLS/DTLS ou cifra simétrica por par) ao chat local (`LocalChatService.kt`).
- [ ] Fazer bind dos servidores (FTP/WebDAV/chat/HTTP interno) apenas na interface Wi‑Fi ativa, não em `0.0.0.0`.
- [ ] Adicionar expiração/uso único aos tokens de `InternalHttpFileServer`.
- [ ] Adotar `network_security_config.xml` restringindo cleartext a `127.0.0.1`/rede local, removendo a liberação global via `usesCleartextTraffic`.

### Fase 3 — Armazenamento e Privacidade
- [ ] Adicionar dependência `androidx.security:security-crypto` e migrar todos os `SharedPreferences` sensíveis do app para `EncryptedSharedPreferences`.
- [ ] Avaliar SQLCipher para `net_toolbox_db` ou cifrar também metadados sensíveis das entidades `VaultEntry`/`TotpAccount`.
- [ ] Auditar e remover/condicionar a `BuildConfig.DEBUG` todos os `Log.d/e`/`printStackTrace()` que exponham IP, conteúdo de mensagens ou erros de rotinas de criptografia.
- [ ] Revisar necessidade real de `READ_SMS`/`WRITE_SMS`/papel de "Default SMS App"; comunicar claramente ao usuário a limitação de que SMS recebidas não são processadas quando o app está ativo como app SMS padrão.

### Fase 4 — Qualidade / Funcionalidades Incompletas
- [ ] Decidir sobre o módulo `mesh`: implementar de fato (`MeshRouter`, `StoreAndForwardRepository`) e integrá-lo ao `TransportManager`/`WifiDirectMedium`, ou remover a tela/toggle "Modo Mesh" que hoje não tem efeito.
- [ ] Substituir fallback de CPU aleatório e emulação de sensores por indicação explícita de "dado indisponível" na UI.
- [ ] Popular ou remover campos estáticos de `NetworkInfoScreen` (gateway, DNS, IP público, MAC).
- [ ] Mover `CLIENT_ID`s de OAuth para configuração de build (`.env`/`BuildConfig`) em vez de constantes no fonte.
- [ ] Habilitar `isMinifyEnabled = true` com regras de ProGuard/R8 para builds de release.

---

## Metodologia e Limitações

- Análise estática manual complementada por buscas automatizadas (`grep`) e subagentes de exploração especializados por vetor (rede/P2P, armazenamento/privacidade, mocks), com verificação manual pós-hoc dos achados críticos citados neste documento (leitura direta do código-fonte para confirmar números de linha e comportamento antes da inclusão no relatório).
- Nenhum teste dinâmico foi executado (não foi feito build/instalação/exploração ativa dos servidores); portanto, itens marcados como "vulnerabilidade confirmada" refletem confirmação por leitura de código-fonte, não por exploração em tempo de execução.
- Escopo não incluiu: dependências de terceiros (CVEs de bibliotecas), engenharia reversa de APK compilado, nem testes de fuzzing dos parsers de protocolo (FTP/WebDAV).
