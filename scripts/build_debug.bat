@echo off
setlocal enabledelayedexpansion

:: Configurações de Ambiente
if "%JAVA_HOME%"=="" (
    set "JAVA_HOME=D:\desenv\java\java-17"
)
if "%ANDROID_HOME%"=="" (
    set "ANDROID_HOME=D:\desenv\Android\SDK"
)

set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

echo ==========================================================
echo  VYKnifeSwiss Quick Build (Debug)
echo ==========================================================
echo JAVA_HOME: %JAVA_HOME%
echo ANDROID_HOME: %ANDROID_HOME%
echo ==========================================================

:: 1. Decodificar debug.keystore usando CertUtil se não existir
if not exist "debug.keystore" (
    if exist "debug.keystore.base64" (
        echo Decodificando debug.keystore de debug.keystore.base64 usando CertUtil...
        certutil -decode debug.keystore.base64 debug.keystore >nul 2>&1
        if %errorlevel% neq 0 (
            echo Erro ao decodificar debug.keystore com CertUtil.
        ) else (
            echo debug.keystore decodificado com sucesso.
        )
    ) else (
        echo Aviso: debug.keystore e debug.keystore.base64 não encontrados. O build de debug pode falhar.
    )
)

:: 2. Criar .env a partir de .env.example se não existir
if not exist ".env" (
    if exist ".env.example" (
        echo Criando arquivo .env a partir de .env.example...
        copy .env.example .env >nul
        echo Arquivo .env criado.
    ) else (
        echo Aviso: .env e .env.example não encontrados.
    )
)

:: 3. Atualizar local.properties para Windows
if not "%ANDROID_HOME%"=="" (
    set "ESC_ANDROID_HOME=%ANDROID_HOME:\=/%"
    echo Atualizando local.properties com sdk.dir=!ESC_ANDROID_HOME!
    (echo sdk.dir=!ESC_ANDROID_HOME!) > local.properties
    echo local.properties atualizado.
)

:: 4. Incrementar versão (conforme versao-pump.md)
echo Incrementando versao do aplicativo antes do build...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$file = 'app/build.gradle.kts';" ^
    "$content = Get-Content $file -Raw;" ^
    "if ($content -match 'versionCode\s*=\s*(\d+)') {" ^
    "    $oldCode = $Matches[1];" ^
    "    $newCode = [int]$oldCode + 1;" ^
    "    $content = $content -replace ('versionCode\s*=\s*' + $oldCode), ('versionCode = ' + $newCode);" ^
    "    Write-Host '   [Versao] versionCode: ' -NoNewline; Write-Host $oldCode -ForegroundColor Red -NoNewline; Write-Host ' -> ' -NoNewline; Write-Host $newCode -ForegroundColor Green;" ^
    "}" ^
    "if ($content -match 'versionName\s*=\s*\"(\d+)\.(\d+)\.(\d+)\"') {" ^
    "    $major = $Matches[1];" ^
    "    $minor = $Matches[2];" ^
    "    $build = [int]$Matches[3] + 1;" ^
    "    $oldName = $Matches[1] + '.' + $Matches[2] + '.' + $Matches[3];" ^
    "    $newName = 'versionName = \"' + $major + '.' + $minor + '.' + $build + '\"';" ^
    "    $content = $content -replace 'versionName\s*=\s*\"\d+\.\d+\.\d+\"', $newName;" ^
    "    Write-Host '   [Versao] versionName: ' -NoNewline; Write-Host $oldName -ForegroundColor Red -NoNewline; Write-Host ' -> ' -NoNewline; Write-Host ($major + '.' + $minor + '.' + $build) -ForegroundColor Green;" ^
    "}" ^
    "Set-Content $file -Value $content -NoNewline;"

:: 5. Executar o build
echo Compilando App com Gradle (assembleDebug)...
call gradlew.bat assembleDebug
if %errorlevel% neq 0 (
    echo Erro na compilacao.
    pause
    exit /b 1
)

:: 6. Mover/Copiar para pasta de outputs
if not exist ".build-outputs" mkdir .build-outputs
echo Copiando APK para .build-outputs...
xcopy /Y "app\build\outputs\apk\debug\app-debug.apk" ".build-outputs\" >nul
echo ==========================================================
echo APK (debug) gerado com sucesso!
echo Destino: .build-outputs\app-debug.apk
echo ==========================================================

echo Build concluido com sucesso!
pause
