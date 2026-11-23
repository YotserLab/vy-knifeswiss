#!/bin/bash
# Script para escanear rede, conectar e instalar APK em dispositivos Android via ADB over TCP/IP

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
ADB_PORT=5555

# Detectar gateway e rede
GATEWAY=$(ip route | grep default | awk '{print $3}')
if [ -z "$GATEWAY" ]; then
    echo "Erro: não foi possível detectar o gateway"
    exit 1
fi

NETWORK=$(echo $GATEWAY | cut -d'.' -f1-3).0/24
echo "Escaneando rede: $NETWORK"
echo "Gateway: $GATEWAY"
echo ""

# Função para tentar conectar o ADB a um IP se a porta 5555 estiver aberta
check_and_connect() {
    local ip=$1
    # Verifica de forma rápida e silenciosa se a porta 5555 está aberta
    if timeout 1.5 bash -c "cat < /dev/null > /dev/tcp/$ip/$ADB_PORT" 2>/dev/null; then
        echo "Porta ADB (5555) aberta em $ip. Tentando conectar..."
        adb connect "$ip:$ADB_PORT"
    fi
}

export -f check_and_connect
export ADB_PORT

# 1. Escanear a rede para encontrar possíveis dispositivos
if command -v nmap &> /dev/null; then
    echo "Usando nmap para escanear porta 5555 (ADB) na rede..."
    IPS=$(nmap -p $ADB_PORT --open -oG - $NETWORK 2>/dev/null | grep Host | awk '{print $2}')
    for IP in $IPS; do
        check_and_connect "$IP"
    done
elif command -v arp-scan &> /dev/null; then
    echo "Usando arp-scan para encontrar IPs ativos e checar porta 5555..."
    # Pega todos os IPs da tabela ARP local
    IPS=$(arp-scan --localnet 2>/dev/null | grep -E "([0-9]{1,3}\.){3}[0-9]{1,3}" | awk '{print $1}')
    for IP in $IPS; do
        check_and_connect "$IP" &
    done
    wait
else
    echo "Nem nmap nem arp-scan encontrados. Executando ping sweep + teste de porta 5555..."
    # Varredura paralela rápida de ping e porta
    for i in {1..254}; do
        IP="${GATEWAY%.*}.$i"
        (
            # Tenta ping básico ou testa direto a porta 5555
            if ping -c 1 -W 1 $IP >/dev/null 2>&1 || timeout 1 bash -c "cat < /dev/null > /dev/tcp/$IP/$ADB_PORT" 2>/dev/null; then
                check_and_connect "$IP"
            fi
        ) &
    done
    wait
fi

echo ""
echo "Dispositivos ADB conectados no momento:"
adb devices -l

echo ""
echo "Instalando APK nos dispositivos conectados via rede..."
# Filtra apenas os dispositivos conectados que possuem formato de IP:Porta
adb devices -l | grep -E "^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:[0-9]+" | while read -r line; do
    IP=$(echo "$line" | awk '{print $1}')
    echo "Instalando em $IP..."
    adb -s "$IP" install -r "$APK_PATH"
    if [ $? -eq 0 ]; then
        echo "  -> Instalação concluída com sucesso em $IP!"
    else
        echo "  -> Falha ao instalar em $IP"
    fi
done

echo ""
echo "Para conectar novos dispositivos via ADB over TCP/IP manualmente:"
echo "1. Conecte o dispositivo via USB"
echo "2. adb tcpip 5555"
echo "3. adb connect <IP>:5555"
echo ""
echo "Para instalar APK manualmente:"
echo "adb -s <IP>:5555 install -r $APK_PATH"
