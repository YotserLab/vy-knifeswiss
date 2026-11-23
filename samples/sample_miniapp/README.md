# VY Mini-App — exemplo de pacote assinado

Este diretório contém um mini-app de exemplo e a ferramenta para assinar pacotes `.vyp`.

## Estrutura do pacote

```
src/
  manifest.json     # metadados e permissões
  index.html        # ponto de entrada
  ...assets
  signature.txt     # assinatura RSA-SHA256 do manifest.json (gerada pelo sign.py)
```

## Gerar o `.vyp`

```bash
cd sample_miniapp
python3 sign.py --src src --key miniapp_private.pem --out out/HelloMini.vyp
```

O arquivo `out/HelloMini.vyp` pode ser instalado no Android através da tela **Mini-Apps**.

## Chaves do protótipo

O par de chaves no diretório `sample_miniapp/` é o mesmo usado pelo app Android em `MiniAppManager.kt`. Em produção, cada desenvolvedor teria sua própria chave privada e o app manteria uma whitelist de chaves públicas confiáveis.

## API JavaScript disponível

No `index.html` você pode chamar:

- `VYBridge.getManifest()` → JSON com os metadados do mini-app.
- `VYBridge.getDeviceInfo()` → modelo do dispositivo e versão do Android.
- `VYBridge.log(message)` → log no Logcat.
- `VYBridge.toast(message)` → Toast nativo.

A ponte é intencionalmente pequena. Novas APIs devem ser adicionadas em `MiniAppRuntimeScreen.kt` na classe `MiniAppBridge`.
