# Spec (gerado; não edite)

**Canivete suíço Android offline-first com ~45 ferramentas locais: rede, arquivos, mídia, cofre, OBD-II, produtividade e controle remoto.**  (porte G)

**Usuários:** usuario: dono do dispositivo; único ator; sem contas nem papéis remotos

**Escopo:** ferramentas de rede local (scanner, ping, Wi-Fi, speed test); gerenciador de arquivos com servidores FTP/WebDAV e clientes remotos; mídia: player, gravadores, transcrição e cast DLNA/Chromecast/Roku; cofre de senhas e TOTP com biometria; informações e testes de hardware do dispositivo; scanner OBD-II via adaptador ELM327 Bluetooth; produtividade: notas, kanban, compras, alarmes, calculadoras; documentos: PDF, EPUB, editor de texto, quadro branco; comunicação: SMS, QR code, chat local P2P, mesh, controle remoto droidlink; transferência web local (WebDrop): HTTP porta 8088 com PIN e QR code; plataforma: dashboard, plugins/miniapps, deep links, histórico, configurações

**Fora de escopo:** contas, login ou sincronização multiusuário em servidor próprio; publicação/distribuição (Play Store) e telemetria de uso; backend ou API pública para clientes externos; suporte a iOS ou desktop

## plataforma (PLAT) - Shell do app: dashboard, navegação, plugins/miniapps, deep links, histórico, configurações e versionamento.
- **Plugin**: id, nome, categoria, entryPoint, permissoes
- **HistoryLog**: id, ferramenta, acao, timestamp
- **Versao**: versionCode, versionName
- Estados Plugin: instalado -> habilitado <-> desabilitado | desinstalado
- `PLAT-R01` deep link vyknifeswiss://tool/<route> abre a ferramenta registrada com essa rota
- `PLAT-R02` deep link vyknifeswiss://oauth é consumido pelo fluxo OAuth do Google Drive
- `PLAT-R03` todo plugin tem manifest plugin.json com id, name, category, entryPoint e permissions
- `PLAT-R04` instalação de miniapp é rejeitada quando manifest.id está em branco
- `PLAT-R05` categoria de plugin ausente ou inválida no manifest cai em PluginCategory.COMMUNITY
- `PLAT-R06` plugins web rodam em WebView e acessam o app só pela ponte VyPluginBridge
- `PLAT-R07` version.properties mantém versionName <major>.<minor>.<build> e versionCode, incrementados antes de build
- `PLAT-R08` ações das ferramentas são gravadas em HistoryLog e listadas na tela Histórico
- `PLAT-R09` a tela Histórico permite limpar todos os registros
- `PLAT-R10` dashboard agrupa ferramentas por PluginCategory a partir do NativePluginRegistry
- `PLAT-R11` toda tela de ferramenta oferece retorno por botão na barra superior e/ou BackHandler
- `PLAT-R12` upgrade de schema Room sem migração apaga e recria o banco (fallbackToDestructiveMigration)
- `PLAT-R13` miniapp instalado fica habilitado por padrão e pode ser desabilitado sem desinstalar
- `PLAT-R14` o app declara android:largeHeap=true para telas que carregam muitos ícones de apps

## rede (RED) - Ferramentas de diagnóstico de rede local: Wi-Fi monitor, port scanner, ping, subnet scanner, speed test e info de rede.
- **ScanResult**: host, porta, aberta, servico
- **SpeedTestResult**: pingMs, downloadMbps, uploadMbps, jitterMs
- **HostAtivo**: ip, latencia, fabricanteOui
- `RED-R01` SubnetScanner varre a sub-rede /24 derivada do IPv4 local
- `RED-R02` descoberta de hosts usa NetworkUtils.ping com 4 pacotes por host
- `RED-R03` resultados de ping no scan são cacheados em memória com timestamp (ScanCache)
- `RED-R04` PortScanner oferece lista de 15 portas comuns pré-definidas
- `RED-R05` teste de porta usa Socket.connect com timeout de 600ms
- `RED-R06` porta aberta é rotulada com serviço conhecido do mapa de NetworkUtils
- `RED-R07` PingScreen executa ping ICMP no host informado e exibe saída por pacote
- `RED-R08` SpeedTest reporta ping em ms (média), download e upload em Mbps e jitter em ms
- `RED-R09` teste de upload usa payload de 2MB
- `RED-R10` conexões do SpeedTest usam connectTimeout de 3s
- `RED-R11` WifiMonitor exibe dados da rede Wi-Fi atual e exige ACCESS_FINE_LOCATION
- `RED-R12` PortScanner e NetworkTest aceitam deep link com host pré-preenchido (rota _with_host)
- `RED-R13` scans de rede rodam sob demanda; não há varredura agendada em background
- `RED-R14` fabricante do adaptador é inferido por tabela OUI dos 3 primeiros bytes do MAC

## chat (CHAT) - Chat local P2P na mesma rede (descoberta UDP, mensagens TCP, áudio UDP) e camada mesh multi-hop com store-and-forward.
- **Peer**: ip, nome, ultimoContato
- **Mensagem**: id, remetente, destinatario, conteudo, timestamp
- **Arquivo**: nome, tamanho, mime, tokenServido
- **MeshMessage**: id, ttlHopsRemaining, ttlTempo, rota
- Estados Peer: descoberto -> conectado -> ausente
- Estados MeshMessage: criada -> encaminhada -> entregue | expirada
- `CHAT-R01` descoberta de peers usa UDP broadcast na porta 9988 com bind IPv4 0.0.0.0
- `CHAT-R02` handshake e mensagens usam TCP na porta 9989 com bind IPv4 0.0.0.0
- `CHAT-R03` áudio de chamada usa UDP na porta 9990
- `CHAT-R04` sweepLocalSubnet varre a sub-rede /24 para achar peers quando broadcast é filtrado
- `CHAT-R05` presença é mantida por handshake HANDSHAKE/PING/PONG sobre TCP
- `CHAT-R06` conexão manual por IP é oferecida no topo e na tela de conversas vazias
- `CHAT-R07` arquivos de até 2MB são enviados inline codificados em Base64 na mensagem
- `CHAT-R08` arquivos acima de 2MB são servidos via InternalHttpFileServer com token na URL
- `CHAT-R09` resposta do servidor de arquivo envia Content-Length exato do arquivo
- `CHAT-R10` conversas e arquivos não saem da rede local; não há servidor intermediário
- `CHAT-R11` mesh decrementa ttlHopsRemaining a cada forward e descarta mensagem quando chega a 0
- `CHAT-R12` mesh descarta sem encaminhar mensagem cujo TTL de tempo expirou
- `CHAT-R13` StoreAndForward retém mensagens para peers offline e limpa as expiradas por TTL
- `CHAT-R14` mesh elege um nó coordenador via CoordinatorElection
- `CHAT-R15` texto de mensagens é cifrado com AES-256-GCM por LocalChatCrypto usando passphrase fixa embutida

## arquivos (ARQ) - Gerenciador de arquivos com abas local/remota/conexões, servidores FTP/WebDAV locais, clientes SFTP, sincronização e backup.
- **Conexao**: tipo, host, porta, usuario, credencial
- **Item**: caminho, nome, tamanho, ehDiretorio, modificadoEm
- **SessaoServidor**: tipo, porta, ativa
- Estados SessaoServidor: parado -> ativo -> parado
- `ARQ-R01` FileManager organiza acesso em abas Local, Remoto e Conexões
- `ARQ-R02` servidor FTP escuta na porta 2221 por padrão, com bind no IP Wi-Fi do dispositivo
- `ARQ-R03` FTP exige USER e PASS; senha comparada em tempo constante; sem auth responde 530
- `ARQ-R04` servidor WebDAV escuta na porta 8080 e exige Basic auth; sem credencial responde 401
- `ARQ-R05` WebDAV decodifica credencial Basic Base64 e valida antes de qualquer método
- `ARQ-R06` conexões remotas suportam protocolos FTP, FTPS, SFTP, WebDAV e SMB
- `ARQ-R07` GoogleDriveProvider lista, baixa, sobe e apaga arquivos via Drive API após OAuth
- `ARQ-R08` sincronização e backup são disparados manualmente pelo usuário
- `ARQ-R09` AppExtractor extrai APK de apps instalados (QUERY_ALL_PACKAGES)
- `ARQ-R10` CacheCleaner abre as configurações de armazenamento do sistema; não limpa cache de terceiros
- `ARQ-R11` exclusão de arquivo é direta via provider.delete; falha mostra toast
- `ARQ-R12` listagem de mídia usa MediaStore respeitando scoped storage (MediaStoreHelper)
- `ARQ-R13` cabeçalho HTTP do InternalHttpFileServer é limitado a 16KB e 64 linhas
- `ARQ-R14` token de arquivo servido expira após o TTL configurado (padrão 15 minutos)
- `ARQ-R15` RemoteFileClient acessa SFTP via JSch (Session + ChannelSftp)

## midia (MED) - Reprodução e captura de mídia (player, gravador de áudio/tela, transcrição) e cast para DLNA, Chromecast e Roku.
- **CastDevice**: nome, tipo, endereco, capacidades
- **ServedMedia**: url, token, ttl
- **Playlist**: id, nome, itens
- **Gravacao**: arquivo, formato, criadaEm
- **CastSession**: dispositivo, midia, estado
- Estados CastSession: desconectado -> conectando -> conectado -> reproduzindo -> desconectado | erro
- `MED-R01` MediaPlayer toca arquivo local com android.media.MediaPlayer
- `MED-R02` gravação de áudio gera REC_yyyyMMdd_HHmmss.m4a em AAC 44,1kHz
- `MED-R03` gravação de áudio roda em foreground service com tipo microphone
- `MED-R04` gravação de tela usa MediaProjection com consentimento e gera SCREEN_yyyyMMdd_HHmmss.mp4
- `MED-R05` biblioteca de áudio lista mp3, m4a, aac e wav do diretório de gravações
- `MED-R06` transcrição usa SpeechRecognizer do sistema com EXTRA_PREFER_OFFLINE e resultados parciais
- `MED-R07` cast descobre dispositivos via SSDP e mDNS (DLNA, Chromecast) e probe Roku ECP
- `MED-R08` URL servida tem TTL = duração da mídia + 30min, com piso de 4 horas
- `MED-R09` CastSessionManager renova o token servido a cada tick de poll de posição
- `MED-R10` sessão DLNA controla AVTransport e RenderingControl via SOAP (volume 0-100)
- `MED-R11` sessão Roku ECP oferece launch e keypress, sem seek nem volume
- `MED-R12` Chromecast usa Google Cast SDK 21.5.0 e RemoteMediaClient para load
- `MED-R13` conexão casa rota por friendly name normalizado; fallback para única rota candidata
- `MED-R14` ao conectar cast, o MediaPlayer local pausa e o arquivo é carregado no dispositivo
- `MED-R15` playlists são persistidas em Room (MediaPlaylist e PlaylistItem)

## cofre (COF) - Cofre de senhas e autenticador TOTP protegidos por biometria, com banco SQLCipher e backup cifrado exportável.
- **VaultEntry**: id, titulo, usuario, senhaCifrada, notas
- **TotpAccount**: id, emissor, conta, segredoCifrado, digitos, periodo, algoritmo
- **VaultBackup**: payloadCifrado, versao
- **Cofre**: desbloqueado
- Estados Cofre: bloqueado -> desbloqueado -> bloqueado
- `COF-R01` entradas do cofre e contas TOTP são persistidas no Room cifrado por SQLCipher
- `COF-R02` passphrase do banco é gerada e guardada em EncryptedSharedPreferences pelo DatabaseKeyManager
- `COF-R03` abrir o cofre exige autenticação via BiometricPrompt
- `COF-R04` revelar ou gerar código TOTP exige BiometricPrompt
- `COF-R05` cancelamento ou erro de biometria mantém o recurso bloqueado
- `COF-R06` TOTP segue RFC 6238: SHA1/SHA256/SHA512, 6 ou 8 dígitos, período padrão 30s
- `COF-R07` segredos TOTP são guardados cifrados em repouso (TotpSecretCrypto)
- `COF-R08` contas TOTP são adicionadas por URI otpauth:// manual ou via scanner QR
- `COF-R09` exportação gera VaultBackupPayload cifrado com AES-256 usando o PIN mestre como senha
- `COF-R10` importação do backup exige o PIN mestre usado na exportação
- `COF-R11` DeviceSecurity avalia bloqueio de tela, biometria, patch, root e a11y sem alterar o sistema
- `COF-R12` tempo restante do código TOTP é calculado como period - (epochSeconds mod period)

## dispositivo (DEV) - Telemetria e testes de hardware do aparelho (DeviceInfo em 14 abas), bússola, nível, terminal e lançador de atividades.
- **AbaInfo**: nome, dadosColetados
- **TesteHardware**: nome, sensorOuAtuador, resultado
- **LeituraSensor**: tipo, eixos, unidade
- Estados TesteHardware: untested -> passed | failed
- `DEV-R01` DeviceInfo lê telemetria real do sistema/hardware, sem dados simulados
- `DEV-R02` DeviceInfo expõe 14 abas de telemetria e testes, de Dashboard a Tests
- `DEV-R03` aba CPU detecta o SoC e mostra frequência por núcleo lida de /sys ao vivo
- `DEV-R04` aba Sensors lista sensores do SensorManager com valores X/Y/Z em tempo real
- `DEV-R05` suíte Tests tem 10 testes interativos: tela, multitouch, lanterna, alto-falante, fone, microfone, proximidade, luz, acelerômetro e vibração
- `DEV-R06` relatório do DeviceInfo pode ser exportado a partir da tela principal
- `DEV-R07` Compass mostra azimute 0-360 graus e o ponto cardeal derivado do azimute
- `DEV-R08` BubbleLevel usa sensores de orientação para indicar inclinação
- `DEV-R09` Terminal executa comandos num shell interno estilo Termux
- `DEV-R10` botão SSH no Terminal dispara com.termux.RUN_COMMAND apontando /data/data/com.termux/files/usr/bin/ssh
- `DEV-R11` ActivityLauncher lista activities de apps instalados e permite lançá-las
- `DEV-R12` lista de apps usa ícone placeholder; drawables de apps não são carregados na UI
- `DEV-R13` sem sensor disponível, a ferramenta informa ausência em vez de quebrar
- `DEV-R14` aba Apps separa apps de usuário e de sistema e oferece busca por texto

## obd2 (OBD) - Scanner OBD-II via adaptador ELM327 Bluetooth: PIDs ao vivo, DTCs, freeze frame, testes ECU, trip computer, dyno e exportação.
- **Pid**: mode, pid, nome, unidade, formula
- **SessaoObd**: id, inicio, fim, logs
- **Dtc**: codigo, descricao, status
- **ConexaoObd**: adaptador, estado
- Estados ConexaoObd: desconectado -> conectando -> conectado -> desconectado | erro
- `OBD-R01` conexão usa adaptador ELM327 via Bluetooth pareado ou WiFi TCP (porta padrão 35000)
- `OBD-R02` polling de PIDs é adaptativo com ciclo rápido de 400ms e ciclos medium/slow
- `OBD-R03` suporta Mode 01, 02, 03, 04, 06, 07 e 09 do padrão OBD-II
- `OBD-R04` limpar DTCs (Mode 04) exige confirmação explícita em diálogo
- `OBD-R05` PIDs padrão seguem SAE J1979 e o registro aceita Custom PIDs OEM do usuário
- `OBD-R06` visão alterna entre Motorista (essencial) e Técnica (todos os sensores)
- `OBD-R07` logs de sessão persistem em Room (ObdSessionEntity e ObdLogEntryEntity)
- `OBD-R08` sessão pode ser exportada em CSV e PDF
- `OBD-R09` I/M Readiness é lida via Mode 01 PID 01
- `OBD-R10` alertas de sensores distinguem severidade normal e crítica (ObdAlert)
- `OBD-R11` trip computer estima consumo e distância; dyno virtual estima potência e torque
- `OBD-R12` sem conexão ativa, a tela mostra estado desconectado e não inventa leituras
- `OBD-R13` inicialização do ELM327 envia ATZ, ATE0, ATL0, ATH0, ATS0, ATM0, ATAT1 e ATSP0 em sequência
- `OBD-R14` socket TCP do adaptador usa soTimeout de 4 segundos
- `OBD-R15` teste de performance suporta 0-100, 0-60, 0-200 km/h e quarto de milha, acumulando resultados na sessão

## produtividade (PROD) - Notas, kanban, lista de compras, alarmes inteligentes e calculadoras (HP-48G, preço, calorias).
- **KanbanColumn**: id, name, position
- **KanbanNote**: id, title, content, columnId, position, colorHex, timestamp
- **ShoppingItem**: id, lista, nome, comprado
- **SmartAlarm**: id, titulo, disparoEm
- Estados KanbanNote: criada -> movida entre colunas -> excluída
- `PROD-R01` colunas do kanban são nomeadas e ordenadas por position
- `PROD-R02` nota do kanban pertence a uma coluna (columnId), tem posição e cor em colorHex
- `PROD-R03` cor padrão de nota kanban nova é #FFF9C4
- `PROD-R04` notas rápidas são persistidas no Room e listadas via getAllNotes
- `PROD-R05` lista de compras aceita itens pré-cadastrados de DefaultShoppingItems
- `PROD-R06` SmartAlarm usa o calendário do sistema (READ/WRITE_CALENDAR)
- `PROD-R07` dados de produtividade ficam no mesmo banco SQLCipher do app
- `PROD-R08` Hp48gCalculator emula a lógica RPN da HP-48G
- `PROD-R09` PriceCalculator compara custo por quantidade entre produtos
- `PROD-R10` CalorieCalculator estima calorias e proteína por peso usando tabela local de alimentos por 100g
- `PROD-R11` calculadoras não enviam dados para fora do dispositivo
- `PROD-R12` na HP-48G, ENTER com buffer vazio duplica o topo da pilha RPN

## documentos (DOC) - Leitura e edição de documentos locais: PDF nativo, EPUB em WebView, editor de texto e quadro branco.
- **Documento**: caminho, formato, paginas
- **Pagina**: indice, bitmap
- `DOC-R01` PdfViewer renderiza páginas com PdfRenderer nativo em RENDER_MODE_FOR_DISPLAY
- `DOC-R02` acesso a páginas do PdfRenderer é sincronizado pois a API não é thread-safe
- `DOC-R03` EpubViewer renderiza em WebView e serve assets internos via esquema epub://
- `DOC-R04` requisições WebView fora do esquema epub:// não recebem conteúdo do epub
- `DOC-R05` TextEditor edita texto plano com abrir e salvar arquivo
- `DOC-R06` Whiteboard permite desenho livre sobre canvas
- `DOC-R07` documentos são abertos a partir de caminho codificado na rota (encodedPath)

## comunicacao (COM) - Comunicação com o mundo externo: geração/leitura de QR, gerenciador SMS (app padrão) e e-mail via Mailtrap.
- **QrPayload**: conteudo, formato
- **SmsThread**: endereco, mensagens
- **Email**: para, assunto, corpo
- `COM-R01` geração de QR usa ZXing QRCodeWriter a partir de texto do usuário
- `COM-R02` leitura de QR/barras usa ML Kit BarcodeScanning com a câmera
- `COM-R03` SmsManager só oferece gestão completa quando o app detém ROLE_SMS
- `COM-R04` sem ROLE_SMS, a tela oferece solicitar o papel via RoleManager
- `COM-R05` SMS recebidos chegam por SmsReceiver quando o app é o padrão
- `COM-R06` envio de SMS passa por HeadlessSmsSendService
- `COM-R07` e-mail usa API Mailtrap em https://send.api.mailtrap.io/api/send
- `COM-R08` MMS recebidos chegam por MmsReceiver
- `COM-R09` o app implementa SmsComposeActivity para responder como app de SMS padrão

## droidlink (DLK) - Controle remoto entre dois aparelhos na rede: protocolo binário WireFrame, sessão cifrada AES-GCM e input via acessibilidade.
- **WireFrame**: versao, tipo, requestId, payload
- **SessaoDroidLink**: host, porta, papel, cifrada
- **PerfilConexao**: host, porta, label
- Estados SessaoDroidLink: ociosa -> conectando -> ativa -> encerrada | erro
- `DLK-R01` WireFrame tem header de 10 bytes: version(1) + type(1) + requestId(4) + payloadLen(4)
- `DLK-R02` versão de protocolo atual é 1; frame com versão diferente é rejeitado no decode
- `DLK-R03` payloadLen máximo por frame é 64MiB; decode lança erro acima disso
- `DLK-R04` FrameType é identificado por código byte; fromCode mapeia código para o tipo
- `DLK-R05` chave de sessão deriva da senha por PBKDF2-HMAC-SHA256, 100 mil iterações, 256 bits
- `DLK-R06` frames cifrados usam AES/GCM/NoPadding com IV de 12 bytes e tag de 128 bits
- `DLK-R07` autenticação de sessão usa HMAC-SHA256 sobre nonce com comparação em tempo constante
- `DLK-R08` nonce e salt são 16 bytes gerados por SecureRandom
- `DLK-R09` servidor escuta em porta configurável, padrão 8989, protegido por senha de exibição
- `DLK-R10` cliente conecta por IP + porta + senha; aceita IP local e Tailscale 100.x
- `DLK-R11` input remoto no servidor é injetado via RemoteInputAccessibilityService
- `DLK-R12` conexões recentes são persistidas em DroidLinkConnectionHistory
- `DLK-R13` áudio do servidor é capturado e transmitido ao cliente (DroidLinkAudioCapture)
- `DLK-R14` autenticação aceita senha efêmera por sessão ou senha fixa salva em SecurePrefs; HMAC é verificado contra ambas as chaves derivadas
- `DLK-R15` DroidLinkSettings cobre áudio, vídeo (qualityMode/targetFps) e permissões remotas: input, ações, arquivos e tela acordada

## extras (EXT) - Ferramentas auxiliares de sistema: guia anti-encerramento, janela flutuante do chat, busca global, equalizador, gravação de tela, monitor Wi-Fi, info de rede, sobre e ajustes.
- **GuiaPowerOff**: fabricante, titulo, passos
- `EXT-R01` PowerOff Protection mostra guia por fabricante/marca e atalhos para configurações do sistema
- `EXT-R02` Local Chat pode abrir em janela flutuante (documentLaunchMode=always, allowEmbedded, redimensionável)
- `EXT-R03` GlobalSearch busca arquivos no armazenamento com filtros por tipo (docs, imagens, vídeos, áudio)
- `EXT-R04` player aplica equalizador global via audiofx.Equalizer na audioSessionId, persistindo bandas
- `EXT-R05` ScreenRecorder grava a tela via MediaProjectionManager + MediaRecorder
- `EXT-R06` WifiMonitor lista redes próximas usando SCAN_RESULTS_AVAILABLE_ACTION do WifiManager
- `EXT-R07` NetworkInfo mostra estado de conectividade via ConnectivityManager
- `EXT-R08` About exibe BuildConfig.VERSION_NAME e VERSION_CODE do app
- `EXT-R09` ajustes por ferramenta persistem em SharedPreferences tool_settings
- `EXT-R10` Dev Settings abre as opções de desenvolvedor do sistema quando o intent existe
- `EXT-R11` MediaManager lista mídia das pastas padrão filtrando por extensão (mp3, mp4, jpg...)
- `EXT-R12` Mesh Info permite ativar mesh e forçar coordenador via prefs local_chat_prefs
- `EXT-R13` histórico do chat local persiste peers e mensagens via LocalChatStorage
- `EXT-R14` servidor HTTP interno renova o TTL do token hospedado via renew() durante a sessão
- `EXT-R15` download de arquivo grava em .tmp e só renomeia para o nome final quando completo

## webdrop (WBD) - Compartilhamento bidirecional de arquivos via navegador web local com PIN de 4 dígitos, QR code e streaming direto de até 2 GiB.
- **SessaoWebDrop**: porta, pin, status, enderecoHttp
- **TransferenciaWebDrop**: id, nomeArquivo, tamanhoBytes, direcao, progresso, status
- **ArquivoCompartilhado**: id, uri, nome, tamanhoBytes, mimeType
- Estados SessaoWebDrop: parada -> ativa -> parada
- Estados TransferenciaWebDrop: pendente -> transmitindo -> concluida | erro
- `WBD-R01` servidor HTTP WebDrop escuta na porta 8088 com bind no IP Wi-Fi local do dispositivo
- `WBD-R02` sessão WebDrop gera PIN numérico aleatório de 4 dígitos a cada inicialização do servidor
- `WBD-R03` UI do app exibe endereço HTTP e QR code com URL contendo o PIN como parâmetro
- `WBD-R04` navegador web autentica via PIN informado na URL ou digitado manualmente na página
- `WBD-R05` requisições web sem PIN válido ou sessão ativa recebem código HTTP 401 Unauthorized
- `WBD-R06` interface web permite arrastar e soltar ou selecionar arquivos para upload ao celular
- `WBD-R07` upload web grava arquivo via streaming direto em disco na pasta Download/WebDrop/
- `WBD-R08` tamanho máximo por arquivo transferido no WebDrop é de 2 GiB; excedente é rejeitado
- `WBD-R09` usuário seleciona arquivos locais do aparelho para disponibilizar para download na web
- `WBD-R10` página web lista arquivos disponibilizados pelo app com link direto de download
- `WBD-R11` falhas de rede ou interrupção de streaming abortam transferência e apagam temporário

## Não funcionais
- `NF01` minSdk 24 e targetSdk 36; app nativo Android em Kotlin + Jetpack Compose
- `NF02` funciona offline; internet só é usada por integrações opt-in (Drive, Mailtrap, speed test)
- `NF03` banco Room net_toolbox_db v6 é cifrado por SQLCipher com passphrase do DatabaseKeyManager
- `NF04` version.properties é incrementado (<major>.<minor>.<build>) antes de cada build
- `NF05` dados do usuário permanecem no dispositivo por padrão; exportação é sempre ação explícita
- `NF06` servidores embutidos (FTP, WebDAV, HTTP interno) fazem bind apenas no IP Wi-Fi local

## Premissas (assumidas, não confirmadas)
- `P01` usuário único sem contas; autenticação significa biometria/PIN local do cofre
- `P02` spec documenta comportamento as-is aprovado como baseline pelo usuário
- `P03` operação do app se resume ao dispositivo; não há ambiente de homologação/produção separado

---
Status do portão: OK
