#!/bin/bash
# Script para fazer build do projeto Android

# Interrompe a execução se houver algum erro
set -e

# Diretório do script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
cd "$DIR"

echo "=== Preparando o ambiente para build ==="

# 1. Decodificar debug.keystore se não existir
if [ ! -f "debug.keystore" ]; then
    if [ -f "debug.keystore.base64" ]; then
        echo "Decodificando debug.keystore de debug.keystore.base64..."
        base64 -d debug.keystore.base64 > debug.keystore
    elif [ -f "$HOME/.android/debug.keystore" ]; then
        echo "Copiando debug.keystore de ~/.android..."
        cp "$HOME/.android/debug.keystore" debug.keystore
    else
        echo "Aviso: debug.keystore e debug.keystore.base64 não encontrados. O build de debug pode falhar."
    fi
fi

# 2. Criar .env a partir de .env.example se não existir
if [ ! -f ".env" ]; then
    if [ -f ".env.example" ]; then
        echo "Criando arquivo .env a partir de .env.example..."
        cp .env.example .env
    else
        echo "Aviso: .env e .env.example não encontrados."
    fi
fi

# 3. Detectar SDK do Android
# Primeiro, tenta ler do local.properties se existir
if [ -f "local.properties" ]; then
    sdk_dir_from_prop=$(grep "^sdk.dir=" local.properties | cut -d'=' -f2-)
    # Expandir ~ se presente no path
    sdk_dir_from_prop="${sdk_dir_from_prop/#\~/$HOME}"
    if [ -n "$sdk_dir_from_prop" ] && [ -d "$sdk_dir_from_prop" ]; then
        export ANDROID_HOME="$sdk_dir_from_prop"
        echo "Android SDK obtido de local.properties: $ANDROID_HOME"
    fi
fi

# Se não foi definido pelo local.properties, usa o do ambiente se existir e for válido
if [ -z "$ANDROID_HOME" ] || [ ! -d "$ANDROID_HOME" ]; then
    if [ -n "$ANDROID_HOME" ]; then
        unset ANDROID_HOME
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
            echo "Android SDK encontrado em: $ANDROID_HOME"
            break
        fi
    done
fi

if [ -z "$ANDROID_HOME" ]; then
    echo "Erro: ANDROID_HOME não está definido e não foi encontrado em locais padrão."
    echo "Por favor, defina a variável de ambiente ANDROID_HOME ou crie o arquivo local.properties."
    exit 1
fi

if [ ! -f "local.properties" ]; then
    echo "Criando local.properties com sdk.dir=$ANDROID_HOME..."
    echo "sdk.dir=$ANDROID_HOME" > local.properties
fi

# 4. Verificar/Gerar Gradle Wrapper
if [ ! -f "gradlew" ]; then
    echo "gradlew não encontrado. Gerando Gradle Wrapper..."
    gradle wrapper --gradle-version 9.3.1
fi

# Dar permissão de execução ao gradlew (ignora erro se já estiver sem permissão de dono na devbox)
chmod +x gradlew 2>/dev/null || true

echo "=== Iniciando o Build ==="

# Auto-incrementar a versão do Build (versionCode) via version.properties
VERSION_PROP_FILE="version.properties"
if [ ! -f "$VERSION_PROP_FILE" ]; then
    echo "versionCode=1" > "$VERSION_PROP_FILE"
    echo "versionName=1.0.0" >> "$VERSION_PROP_FILE"
fi

OLD_VERSION_CODE=$(grep "^versionCode=" "$VERSION_PROP_FILE" | cut -d'=' -f2)
VERSION_NAME=$(grep "^versionName=" "$VERSION_PROP_FILE" | cut -d'=' -f2)
if [ -n "$OLD_VERSION_CODE" ]; then
    NEW_VERSION_CODE=$((OLD_VERSION_CODE + 1))
    printf "versionCode=%s\nversionName=%s\n" "$NEW_VERSION_CODE" "$VERSION_NAME" > "$VERSION_PROP_FILE"
    echo ">> Build incrementado: $OLD_VERSION_CODE -> $NEW_VERSION_CODE"
fi

# Extrair a versão atual do app
VERSION_NAME=$(grep "^versionName=" "$VERSION_PROP_FILE" | cut -d'=' -f2)
VERSION_CODE=$(grep "^versionCode=" "$VERSION_PROP_FILE" | cut -d'=' -f2)
if [ -n "$VERSION_NAME" ] && [ -n "$VERSION_CODE" ]; then
    echo "=========================================="
    echo "  Construindo Versão: $VERSION_NAME (Build $VERSION_CODE)"
    echo "=========================================="
fi

# Calcular dinamicamente 80% das CPUs disponíveis (mínimo 1)
TOTAL_CORES=$(nproc 2>/dev/null || echo 4)
GRADLE_WORKERS=$(( TOTAL_CORES * 80 / 100 ))
[ "$GRADLE_WORKERS" -lt 1 ] && GRADLE_WORKERS=1
echo ">> Utilizando $GRADLE_WORKERS de $TOTAL_CORES núcleos (~80% da CPU) para compilação"

# Executa o build (compilação e testes)
if [ "$1" = "debug" ]; then
    echo "Gerando build de DEBUG..."
    ./gradlew --max-workers="$GRADLE_WORKERS" assembleDebug
else
    echo "Gerando build de RELEASE..."
    ./gradlew --max-workers="$GRADLE_WORKERS" assembleRelease
fi

echo "=== Build concluído com sucesso! ==="
