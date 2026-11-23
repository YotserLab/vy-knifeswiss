#!/bin/bash

echo "=========================================================="
echo "          Pareamento Rápido - ADB Wireless"
echo "=========================================================="
echo "No celular: Opções de Desenvolvedor -> Depuração sem fio -> Parear dispositivo com código"
echo ""

read -p "1. Digite o IP:PORTA de pareamento (ex: 100.103.206.111:38193): " PAIR_ADDR
read -p "2. Digite o PIN de 6 dígitos: " PAIR_PIN

echo ""
echo ">>> Pareando com $PAIR_ADDR..."
adb pair "$PAIR_ADDR" "$PAIR_PIN"

echo ""
echo "Agora olhe a tela inicial de 'Depuração sem fio' no celular."
echo "O IP deve ser o mesmo, mas a PORTA é diferente."
read -p "3. Qual é a PORTA principal de conexão? (ex: 41235): " CONNECT_PORT

# Extrai apenas o IP do PAIR_ADDR (tudo antes do ':')
IP=$(echo "$PAIR_ADDR" | cut -d':' -f1)

echo ""
echo ">>> Conectando no dispositivo $IP:$CONNECT_PORT..."
adb connect "$IP:$CONNECT_PORT"

echo ""
echo ">>> Dispositivos ADB Conectados:"
adb devices
echo "=========================================================="
