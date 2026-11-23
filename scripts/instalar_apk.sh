#!/bin/bash
# Script flexível e parametrizável para instalar o APK no dispositivo móvel

# Interrompe a execução se houver algum erro
set -e

# Diretório do script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
cd "$DIR"

# Parâmetros padrão
TARGET_DEVICE=""
BUILD_TYPE=""
CUSTOM_APK=""

usage() {
    echo "=========================================================="
    echo " Script de Instalação de APK — VYKnifeSwiss"
    echo "=========================================================="
    echo "Uso: $0 [opções] [dispositivo_id] [release|debug|caminho_do_apk]"
    echo ""
    echo "Opções:"
    echo "  -s, --device ID      Instala apenas no dispositivo especificado por ID"
    echo "  -r, --release        Instala a versão release (app-release.apk)"
    echo "  -d, --debug          Instala a versão debug (app-debug.apk - padrão)"
    echo "  -f, --file CAMINHO   Especifica um arquivo APK customizado"
    echo "  -h, --help           Exibe esta ajuda"
    echo ""
    echo "Exemplos:"
    echo "  $0                   # Instala APK debug em todos os dispositivos"
    echo "  $0 5a8d17            # Instala apenas no dispositivo 5a8d17"
    echo "  $0 release           # Instala APK release em todos os dispositivos"
    echo "  $0 release 5a8d17    # Instala APK release apenas no dispositivo 5a8d17"
    echo "  $0 /caminho/meu.apk  # Instala um arquivo APK customizado"
    echo "=========================================================="
    exit 0
}

# Parse de argumentos
while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)
            usage
            ;;
        -r|--release|release)
            BUILD_TYPE="release"
            shift
            ;;
        -d|--debug|debug)
            BUILD_TYPE="debug"
            shift
            ;;
        -s|--device)
            TARGET_DEVICE="$2"
            shift 2
            ;;
        -f|--file)
            CUSTOM_APK="$2"
            shift 2
            ;;
        *.apk)
            CUSTOM_APK="$1"
            shift
            ;;
        *)
            if [ -f "$1" ]; then
                CUSTOM_APK="$1"
            else
                TARGET_DEVICE="$1"
            fi
            shift
            ;;
    esac
done

# Determinar caminho do APK
if [ -n "$CUSTOM_APK" ]; then
    APK_PATH="$CUSTOM_APK"
elif [ "$BUILD_TYPE" = "release" ]; then
    APK_PATH="app/build/outputs/apk/release/app-release.apk"
elif [ "$BUILD_TYPE" = "debug" ]; then
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
else
    # Auto-detectar o APK mais recente construído
    if [ -f "app/build/outputs/apk/release/app-release.apk" ] && [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
        if [ "app/build/outputs/apk/release/app-release.apk" -nt "app/build/outputs/apk/debug/app-debug.apk" ]; then
            APK_PATH="app/build/outputs/apk/release/app-release.apk"
        else
            APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
        fi
    elif [ -f "app/build/outputs/apk/release/app-release.apk" ]; then
        APK_PATH="app/build/outputs/apk/release/app-release.apk"
    else
        APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    fi
fi

if [ ! -f "$APK_PATH" ]; then
    echo "Erro: APK não encontrado em $APK_PATH"
    echo "Por favor, execute './scripts/build.sh' primeiro para compilar o app."
    exit 1
fi

# Detectar Android SDK
if [ -z "$ANDROID_HOME" ]; then
    if [ -f "local.properties" ]; then
        sdk_dir_from_prop=$(grep "^sdk.dir=" local.properties | cut -d'=' -f2-)
        sdk_dir_from_prop="${sdk_dir_from_prop/#\~/$HOME}"
        if [ -n "$sdk_dir_from_prop" ] && [ -d "$sdk_dir_from_prop" ]; then
            export ANDROID_HOME="$sdk_dir_from_prop"
        fi
    fi
fi
if [ -z "$ANDROID_HOME" ]; then
    POSSIBLE_PATHS=(
        "$HOME/Android/Sdk"
        "$HOME/android-sdk"
        "/usr/lib/android-sdk"
        "/opt/android-sdk"
    )
    for path in "${POSSIBLE_PATHS[@]}"; do
        if [ -d "$path" ]; then
            export ANDROID_HOME="$path"
            break
        fi
    done
fi

# Informações do APK (aapt)
AAPT_CMD=""
if command -v aapt &> /dev/null; then
    AAPT_CMD="aapt"
elif [ -n "$ANDROID_HOME" ]; then
    AAPT_CMD=$(find "$ANDROID_HOME/build-tools" -name "aapt" 2>/dev/null | sort -V | tail -1)
fi

if [ -n "$AAPT_CMD" ]; then
    BADGING=$("$AAPT_CMD" dump badging "$APK_PATH" 2>/dev/null || true)
    if [ -n "$BADGING" ]; then
        VERSION_NAME=$(echo "$BADGING" | grep "package: name=" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")
        VERSION_CODE=$(echo "$BADGING" | grep "package: name=" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p")
        echo "=== Informações do APK a ser instalado ==="
        echo "Arquivo: $APK_PATH"
        echo "Versão: $VERSION_NAME (Build $VERSION_CODE)"
        echo "=========================================="
        echo ""
    fi
fi

# Procurar pelo executável do adb
ADB_CMD="adb"
if ! command -v adb &> /dev/null; then
    if [ -n "$ANDROID_HOME" ] && [ -f "$ANDROID_HOME/platform-tools/adb" ]; then
        ADB_CMD="$ANDROID_HOME/platform-tools/adb"
    else
        echo "Erro: ADB (Android Debug Bridge) não foi encontrado."
        exit 1
    fi
fi

echo "=== Verificando dispositivos conectados ==="
DEVICES=$("$ADB_CMD" devices | grep -E "[[:space:]]device$" || true)

if [ -z "$DEVICES" ]; then
    echo "Erro: Nenhum dispositivo móvel ou emulador conectado via ADB."
    exit 1
fi

DEVICE_IDS=$(echo "$DEVICES" | awk '{print $1}')

# Filtrar por dispositivo alvo se especificado
if [ -n "$TARGET_DEVICE" ]; then
    if echo "$DEVICE_IDS" | grep -q "$TARGET_DEVICE"; then
        DEVICE_IDS="$TARGET_DEVICE"
        echo "Filtro ativo: Instalando APENAS no dispositivo $TARGET_DEVICE"
    else
        echo "Erro: Dispositivo '$TARGET_DEVICE' não foi encontrado entre os conectados:"
        echo "$DEVICES"
        exit 1
    fi
else
    echo "Dispositivo(s) encontrado(s):"
    echo "$DEVICES"
fi

# Executar a instalação
echo "=== Instalando o APK ==="

for DEVICE_ID in $DEVICE_IDS; do
    echo "----------------------------------------------------------"
    echo "Instalando em $DEVICE_ID..."
    echo "Instalando $APK_PATH em $DEVICE_ID..."

    if ! "$ADB_CMD" -s "$DEVICE_ID" install -r -d "$APK_PATH" 2>&1; then
        INSTALL_OUTPUT=$("$ADB_CMD" -s "$DEVICE_ID" install -r -d "$APK_PATH" 2>&1 || true)
        if echo "$INSTALL_OUTPUT" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
            echo ""
            echo "Conflito de assinatura detectado em $DEVICE_ID. Tentando desinstalar o app existente..."

            PACKAGE_ID=""
            if [ -n "$AAPT_CMD" ]; then
                PACKAGE_ID=$("$AAPT_CMD" dump badging "$APK_PATH" 2>/dev/null | grep "package: name=" | sed "s/package: name='//;s/'.*//")
            fi

            if [ -z "$PACKAGE_ID" ] && [ -n "$ANDROID_HOME" ]; then
                AAPT2_CMD=$(find "$ANDROID_HOME/build-tools" -name "aapt2" 2>/dev/null | sort -V | tail -1)
                if [ -n "$AAPT2_CMD" ]; then
                    PACKAGE_ID=$("$AAPT2_CMD" dump badging "$APK_PATH" 2>/dev/null | grep "package: name=" | sed "s/package: name='//;s/'.*//")
                fi
            fi

            if [ -n "$PACKAGE_ID" ]; then
                echo "Package ID: $PACKAGE_ID"
                echo "Desinstalando $PACKAGE_ID do dispositivo $DEVICE_ID..."
                "$ADB_CMD" -s "$DEVICE_ID" uninstall "$PACKAGE_ID"
                echo "Reinstalando $APK_PATH em $DEVICE_ID..."
                if ! "$ADB_CMD" -s "$DEVICE_ID" install -d "$APK_PATH"; then
                    echo "Aviso: Falha final ao instalar no dispositivo $DEVICE_ID. Pulando."
                    continue
                fi
            else
                echo "Aviso: Erro ao desinstalar automaticamente em $DEVICE_ID. Pulando."
                continue
            fi
        else
            echo "Aviso: Falha ao instalar no dispositivo $DEVICE_ID. Pulando."
            echo "$INSTALL_OUTPUT"
            continue
        fi
    fi
done

echo "=========================================================="
echo "Instalação concluída com sucesso!"
echo "=========================================================="
