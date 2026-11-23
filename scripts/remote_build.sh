#!/bin/bash

# Configurações
PROJECT_DIR="/projetos/apps-mobile/android/VYKnifeSwiss"
DEVBOX="devbox"

echo "==========================================="
echo "🚀 Iniciando Build Remoto (Devbox)"
echo "==========================================="

echo ""
echo "[1/3] 📤 Sincronizando código local com a Devbox..."
# Remove output de debug, mantem sincronia rapida
./sync-L2R.sh > /dev/null

echo ""
echo "[2/3] 🔨 Executando build na Devbox via SSH..."
# Executa o build.sh que já contém a lógica para encontrar o ANDROID_HOME correto na devbox
ssh $DEVBOX "source ~/.profile 2>/dev/null || source ~/.bashrc 2>/dev/null; cd $PROJECT_DIR && bash build.sh"

if [ $? -eq 0 ]; then
    echo ""
    echo "[3/3] 📥 Trazendo o APK gerado de volta..."
    ./sync-R2L.sh > /dev/null
    echo ""
    echo "✅ Build remoto concluído com sucesso!"
    echo "📂 O APK pode ser encontrado na pasta app/build/outputs/apk/debug/"
else
    echo ""
    echo "❌ Erro durante o build na Devbox."
    exit 1
fi
