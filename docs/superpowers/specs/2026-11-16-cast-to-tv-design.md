# Spec — Módulo "Cast to TV" (clone parcial do XCast)

**Data:** 2026-11-16
**Status:** Aprovado pelo usuário (design) — aguardando revisão do spec
**Escopo aprovado:** Descoberta de dispositivos + Cast de mídia local, como módulo novo integrado ao FileManager e MediaPlayer existentes. Protocolos: DLNA manual + Roku ECP manual + Google Cast SDK (Chromecast).

## 1. Objetivo

Replicar do app "Cast to TV – XCast" as funcionalidades de:
- **Descoberta de dispositivos** na rede local (TVs DLNA/UPnP, Roku, Chromecast, dispositivos DIAL).
- **Cast de mídia local** (vídeo, áudio, foto) para o dispositivo selecionado, com mini-controle remoto (play/pause/seek/stop/volume).

## 2. Fora de escopo (não implementar)

- Screen mirroring / espelhamento de tela.
- Navegador web embutido para detecção de vídeos (web video cast).
- IPTV / playlists M3U.
- Lançamento de apps por DIAL (YouTube/Netflix/Prime Video na TV).
- Transcodificação de formatos não suportados pelo renderer.
- Cast de mídia remota (só arquivos locais do dispositivo).

## 3. Arquitetura

Novo pacote `com.example.cast` (mesmo padrão de `com.example.droidlink`):

```
cast/
├── discovery/
│   ├── CastDevice.kt          # model: id, name, type, ip, port, controlUrls, manufacturer/model
│   ├── SsdpDiscovery.kt       # M-SEARCH multicast + fetch/parse do device-description XML
│   ├── MdnsCastDiscovery.kt   # NsdManager _googlecast._tcp → Chromecast
│   ├── RokuProbe.kt           # probe ECP (GET :8060/query/device-info) p/ confirmar Roku
│   └── CastDiscovery.kt       # facade: StateFlow<List<CastDevice>>, dedup, classificação
├── control/
│   ├── CastSession.kt         # interface: load(media,url), play, pause, seek, stop, setVolume, close
│   ├── DlnaSession.kt         # SOAP AVTransport + RenderingControl via socket TCP bruto
│   ├── RokuSession.kt         # ECP: POST /launch/15985 (Play on Roku) + /keypress/*
│   ├── ChromecastSession.kt   # wrapper do RemoteMediaClient (Cast SDK)
│   ├── CastSessionManager.kt  # sessão ativa, polling de posição (1s), estado exposto em Flow
│   └── LanHttpClient.kt       # helper HTTP sobre socket TCP bruto (GET/POST) p/ LAN cleartext
├── media/
│   ├── CastMedia.kt           # model: path/uri, title, mimeType, kind (VIDEO/AUDIO/IMAGE), duration
│   ├── CastMediaServer.kt     # wrapper do InternalHttpFileServer → URL LAN completa
│   └── MediaLibraryQuery.kt   # MediaStore vídeo/áudio/imagem via MediaStoreHelper existente
└── ui/
    ├── CastScreen.kt          # tela do módulo: devices + abas Vídeo/Foto/Áudio
    ├── CastDevicePicker.kt    # bottom sheet reutilizável de seleção de dispositivo
    ├── CastRemoteSheet.kt     # mini-player: play/pause/seek/posição/volume/stop/disconnect
    └── CastViewModel.kt       # estado: devices, sessão, posição, mídia atual
```

## 4. Protocolos — detalhes

### 4.1 Descoberta

| Fonte | Método | Detecta |
|---|---|---|
| SSDP M-SEARCH | UDP multicast `239.255.255.250:1900`, STs: `ssdp:all`, `urn:schemas-upnp-org:device:MediaRenderer:1`, `urn:dial-multiscreen-org:service:dial:1`, `roku:ecp`. 3 envios (~1s), janela ~4s, `MulticastLock` | TVs DLNA, DIAL, Roku |
| Device-desc XML | GET da `LOCATION` via `LanHttpClient`; parse `friendlyName`, `manufacturer`, `modelName`, `UDN`, `serviceList` (controlURL de `AVTransport` e `RenderingControl`) | Classifica DLNA vs DIAL-only |
| mDNS | `NsdManager` em `_googlecast._tcp.local.` (reuso do padrão `NetworkUtils.startMdnsDiscovery`) | Chromecast/Android TV/Google TV |
| Roku probe | `GET http://<ip>:8060/query/device-info` quando SSDP indica `roku:ecp` ou IP manual | Confirma modelo Roku |

**Dedup:** por IP, com precedência `CHROMECAST > DLNA > ROKU > DIAL` (Android TV/Google TV frequentemente é detectado por múltiplas fontes; o protocolo com controle mais completo vence). DIAL-only sem Chromecast → tipo `DIAL` (mostrado como "limitado": sem cast de mídia local).

**Fallback sem multicast:** campo "Adicionar por IP" (mesma convenção do LocalChat) → probe Roku 8060 + tentativa de description DLNA em portas comuns via SSDP unicast M-SEARCH direcionado ao IP.

### 4.2 DLNA (AVTransport + RenderingControl)

SOAP 1.0 via `LanHttpClient` (POST no `controlURL`, header `SOAPACTION`):

- `SetAVTransportURI` com `CurrentURIMetaData` = DIDL-Lite:
  - `upnp:class`: `object.item.videoItem` / `object.item.audioItem.musicTrack` / `object.item.imageItem.photo`
  - `<res protocolInfo="http-get:*:<mime>:*" duration="H:MM:SS">` com a URL LAN
- `Play` (Speed=1), `Pause`, `Stop`, `Seek` (Unit=REL_TIME, Target=H:MM:SS)
- `GetPositionInfo` (polling 1s; RelTime/TrackDuration) + `GetTransportInfo`
- `RenderingControl`: `GetVolume`/`SetVolume` (Channel=Master)

Falhas de parse/ação → sessão reporta erro; UI mostra toast e mantém sessão se transporte continuar PLAYING.

### 4.3 Roku (ECP)

- Play on Roku: `POST /launch/15985?t=<v|a|p>&u=<urlEncoded>&videoName=<name>`
  (`15985` = Roku Media Player; `t=v` vídeo, `t=a` áudio, `t=p` foto)
- Controles limitados: `/keypress/Play`, `/keypress/Back`, `/keypress/Home`, `/keypress/Fwd`, `/keypress/Rev`; `GET /query/active-app`
- Sem seek preciso nem posição → UI exibe controles reduzidos (play/pause/fwd/rev/stop).

### 4.4 Chromecast (Google Cast SDK)

- Deps novas: `com.google.android.gms:play-services-cast-framework` (versão estável recente ≥7 dias) + `androidx.mediarouter:mediarouter`.
- `CastOptionsProvider` (meta-data no manifest): `receiverApplicationId = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID`.
- Conexão **programática** (sem CastButton): `MediaRouter` com `MediaRouteSelector` do app ID → `selectRoute(route)` → Cast SDK cria `CastSession` automaticamente.
- Cast: `RemoteMediaClient.load(MediaLoadRequestData)` com `MediaInfo(contentUrl=URL LAN, contentType=mime)` + `MediaMetadata(title, imagem)`.
- Controles completos via `RemoteMediaClient` + `MediaStatus` listener (play/pause/seek/stop/volume/posição).
- Requisitos já atendidos: `MainActivity : AppCompatActivity`, Play Services já presente no projeto (Firebase/Location).
- Sem GMS no aparelho → Chromecast aparece mas marcado "indisponível neste aparelho".

## 5. Servidor de mídia local (extensão do `InternalHttpFileServer`)

O servidor atual serve `GET /download/<token>` sempre com `application/octet-stream`, sem `Range` e TTL fixo de 15 min. Para cast é necessário:

- **`mimeType` opcional** em `hostFile`/`hostUri` → `Content-Type` correto (DLNA e Chromecast validam; sem isso alguns renderers recusam).
- **HTTP Range** (`Range: bytes=a-b` → `206 Partial Content` com `Content-Range`, `Accept-Ranges: bytes`): necessário para seek/trick-play na maioria das TVs.
- **TTL configurável** (parâmetro opcional, ex.: duração da mídia + margem, ou renovação enquanto a sessão estiver ativa): filmes excedem 15 min.
- URL montada: `http://<ipLocal>:<port>/download/<token>` via `NetworkUtils.getWifiOrLocalIpSync()`.

Mudanças **backward-compatible** (parâmetros com default; LocalChat não é afetado). Reaproveitar `MediaStoreHelper.queryVideos/queryAudio/queryImages` (`FileItem.path` é path real — `MANAGE_EXTERNAL_STORAGE` + `requestLegacyExternalStorage` já concedem acesso direto a `File`).

## 6. Integração no app

- **Módulo:** `Screen.CastToTV` (`route = "cast_to_tv"`), entrada no NavHost, `ToolItem` no Dashboard (categoria compatível com as existentes), deep link `vyknifeswiss://tool/cast_to_tv`.
- **FileManager:** item "Transmitir para TV" no menu de contexto de arquivos com tipo VIDEO/AUDIO/IMAGE → `CastDevicePicker` → cast → `CastRemoteSheet`.
- **MediaPlayerScreen:** ícone de cast na top bar → mesmo fluxo com a mídia atual.
- **Manifest:** meta-data `com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME`. Nenhuma permissão nova necessária (`CHANGE_WIFI_MULTICAST_STATE`, `INTERNET`, `ACCESS_NETWORK_STATE` já existem).

## 7. UI

- **CastScreen:** top bar (título, refresh, banner se sem Wi-Fi); card de dispositivos com scan + lista (ícone por tipo, nome, fabricante/modelo, status); abas Vídeo/Fotos/Áudio com thumbs (Coil já disponível); tap em mídia → cast direto se conectado, senão abre picker.
- **CastDevicePicker:** bottom sheet com scan animado + lista + opção "Adicionar por IP".
- **CastRemoteSheet:** mini-player com thumb, título, seek bar + posição/duração, play/pause, volume, stop/disconnect. Em Roku: seek/volume ocultos ou limitados.

## 8. Segurança (conforme `.agents/skills/secure_coding`)

- Todo tráfego HTTP LAN (SSDP fetch, SOAP, ECP, mídia) via **socket TCP bruto** (`LanHttpClient`) — mesma convenção de `NetworkUtils.downloadFile`; mantém `usesCleartextTraffic=false` global.
- Nenhum componente exportado novo; `CastOptionsProvider` é apenas meta-data.
- `InternalHttpFileServer` já serve apenas tokens registrados (nenhum path traversal); manter.
- Sem secrets, sem logs de dados sensíveis (somente `BuildConfig.DEBUG`).

## 9. Testes (TDD)

JUnit/Robolectric (infra existente) para lógica pura:
- Parse de resposta SSDP (headers, LOCATION, ST/USN).
- Parse de device-description XML (XmlPullParser): friendlyName, serviceList, controlURL relativo→absoluto.
- Construção de envelopes SOAP e DIDL-Lite (escape XML, classes por mime).
- Parse de `GetPositionInfo` (RelTime/TrackDuration H:MM:SS → ms).
- Construção de URLs ECP (`t=`, `u=` url-encoded).
- Detecção de mime por extensão (reusar/extender tabelas `VIDEO_EXTS` etc.).
- Parse de `Range` no servidor HTTP e resposta 206.

Validação manual em dispositivos físicos (convenção do projeto): TVs DLNA, Roku e Chromecast reais conforme disponibilidade.

## 10. Erros e edge cases

- Sem Wi-Fi → banner "conecte-se à mesma rede da TV" (detecção via `ConnectivityManager`, sem depender de SSID/permissão de localização).
- Renderer recusa URL/mime → toast "formato pode não ser suportado pela TV".
- Device sai da rede → sessão encerra, UI volta à lista.
- `GetPositionInfo` falha repetidamente → marca posição como indisponível, mantém controles básicos.
- TTL expira durante reprodução → `CastMediaServer` renova hosting enquanto sessão ativa.

## 11. Build / versionamento

- Bump de `version.properties` (`<major>.<minor>.<build>`, regra versao-pump) antes de compilar.
- `minSdk 24` OK para Cast SDK; `compileSdk 34`.
- Após implementação: atualizar `HANDOFF.md`, registrar em `LOGWORK.md`, commit(s) em conventional commits.
