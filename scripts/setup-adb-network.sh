#!/bin/bash
# Script para configurar dispositivos Android para ADB over TCP/IP
# Requer conexão USB na primeira vez

configure_device() {
    SERIAL=$1
    echo ""
    echo "Configurando dispositivo: $SERIAL"
    
    # Obter IP do dispositivo
    IP=$(adb -s $SERIAL shell ip addr show wlan0 | grep "inet " | awk '{print $2}' | cut -d'/' -f1)
    
    if [ -z "$IP" ]; then
        echo "Erro: não foi possível obter o IP do dispositivo"
        return 1
    fi
    
    echo "IP do dispositivo: $IP"
    
    # Configurar ADB over TCP/IP
    echo "Ativando ADB over TCP/IP na porta 5555..."
    adb -s $SERIAL tcpip 5555
    
    if [ $? -ne 0 ]; then
        echo "Erro ao ativar ADB over TCP/IP"
        return 1
    fi
    
    # Conectar via rede
    echo "Conectando via rede: $IP:5555"
    adb connect $IP:5555
    
    if [ $? -eq 0 ]; then
        echo "Sucesso! Dispositivo configurado para ADB over TCP/IP"
        echo "Você pode desconectar o cabo USB agora"
        echo ""
        echo "Para conectar novamente no futuro:"
        echo "  adb connect $IP:5555"
    else
        echo "Erro ao conectar via rede"
        return 1
    fi
}

echo "=== Configurar ADB over TCP/IP ==="
echo ""
echo "Dispositivos USB conectados:"
adb devices -l | grep -v "List of devices" | grep -v "transport_id" | while read line; do
    if [ -n "$line" ]; then
        SERIAL=$(echo $line | awk '{print $1}')
        MODEL=$(echo $line | sed 's/.*model:\([^ ]*\).*/\1/')
        echo "  - $SERIAL ($MODEL)"
    fi
done

echo ""
echo "Digite o serial do dispositivo que deseja configurar (ou Enter para todos):"
read SERIAL

if [ -z "$SERIAL" ]; then
    echo "Configurando todos os dispositivos USB..."
    adb devices -l | grep -v "List of devices" | grep -v "transport_id" | while read line; do
        if [ -n "$line" ]; then
            SERIAL=$(echo $line | awk '{print $1}')
            configure_device $SERIAL
        fi
    done
else
    configure_device $SERIAL
fi

echo ""
echo "Dispositivos configurados:"
adb devices -l
