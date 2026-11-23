# VY Swiss Knife - Chat Local Desktop

Cliente desktop do chat local para conversar com o app Android `VY Swiss Knife`.

## Compatibilidade

Funciona em **Windows**, **Linux** e macOS (qualquer sistema com Python 3.10+).

## Instalação

1. Instale o Python 3.10 ou superior.
2. No terminal/PowerShell, dentro desta pasta, execute:

```bash
pip install -r requirements.txt
```

## Uso

```bash
python chat.py
```

A janela mostrará seu IP local. Para encontrar dispositivos Android, certifique-se de que:

- O app Android e o computador estão na mesma rede Wi-Fi (ou VPN, como Tailscale).
- O app Android está com a tela de **Chat Local** aberta ou o serviço de chat em segundo plano ativado.

## Como funciona

- Descoberta via **UDP broadcast** na porta `9988`.
- Mensagens via **TCP** na porta `9989`.
- Criptografia AES-GCM com a mesma chave derivada do app Android, garantindo que as mensagens trocadas sejam legíveis nos dois lados.

## Gerando um executável Windows (.exe)

Caso queira distribuir sem exigir Python instalado:

```bash
pip install pyinstaller
pyinstaller --onefile --windowed --name VYChat chat.py
```

O arquivo `.exe` ficará em `dist/VYChat.exe`.
