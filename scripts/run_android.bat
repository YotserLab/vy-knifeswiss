@echo off
setlocal enabledelayedexpansion

:: Configurações de Ambiente (prioriza variáveis do sistema se existirem)
if "%JAVA_HOME%"=="" (
    set "JAVA_HOME=D:\desenv\java\java-17"
)
if "%ANDROID_HOME%"=="" (
    set "ANDROID_HOME=D:\desenv\Android\SDK"
)

:: Forçar uso do Java 17
set "GRADLE_JAVA_HOME=%JAVA_HOME%"

set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

echo ==========================================================
echo  Iniciando VYKnifeSwiss Build ^& Deploy (Windows)...
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

:: Menu Principal
:menu
echo.
echo Escolha uma opcao:
echo   1. Build ^& Instalar Debug APK (Completo)
echo   2. Build ^& Instalar Release APK (Completo)
echo   3. Apenas Build Debug APK (Sem instalar)
echo   4. Apenas Build Release APK (Sem instalar)
echo   5. Apenas Incrementar Versao (Pump)
echo   6. Limpar Cache do Gradle (Clean)
echo   7. Sair
echo.
set /p opcao=Digite a opcao desejada: 

if "%opcao%"=="1" goto op_build_debug
if "%opcao%"=="2" goto op_build_release
if "%opcao%"=="3" goto op_only_debug
if "%opcao%"=="4" goto op_only_release
if "%opcao%"=="5" goto op_pump
if "%opcao%"=="6" goto op_clean
if "%opcao%"=="7" goto :eof
echo Opcao invalida! Tente novamente.
goto menu

:op_pump
echo Incrementando versao do aplicativo...
call :increment_version
goto menu

:op_clean
echo Limpando cache do Gradle...
call gradlew.bat clean
if %errorlevel% neq 0 (
    echo Erro ao limpar o Gradle.
) else (
    echo Gradle limpo com sucesso.
)
goto menu

:op_build_debug
set "BUILD_TYPE=debug"
set "GRADLE_TASK=assembleDebug"
set "APK_SRC=app\build\outputs\apk\debug\app-debug.apk"
set "APK_DEST=.build-outputs\app-debug.apk"
goto process_build

:op_build_release
set "BUILD_TYPE=release"
set "GRADLE_TASK=assembleRelease"
set "APK_SRC=app\build\outputs\apk\release\app-release.apk"
set "APK_DEST=.build-outputs\app-release.apk"
goto process_build

:op_only_debug
set "BUILD_TYPE=debug"
set "GRADLE_TASK=assembleDebug"
set "APK_SRC=app\build\outputs\apk\debug\app-debug.apk"
set "APK_DEST=.build-outputs\app-debug.apk"
set "SKIP_INSTALL=true"
goto process_build_only

:op_only_release
set "BUILD_TYPE=release"
set "GRADLE_TASK=assembleRelease"
set "APK_SRC=app\build\outputs\apk\release\app-release.apk"
set "APK_DEST=.build-outputs\app-release.apk"
set "SKIP_INSTALL=true"
goto process_build_only

:process_build
set "SKIP_INSTALL=false"

:process_build_only
:: Incrementar versão (conforme versao-pump.md)
echo Incrementando versao do aplicativo antes do build...
call :increment_version

:: Executar o build
echo Compilando App com Gradle (%GRADLE_TASK%)...
call gradlew.bat %GRADLE_TASK%
if %errorlevel% neq 0 (
    echo Erro na compilacao.
    pause
    goto menu
)

:: Mover/Copiar para pasta de outputs
if not exist ".build-outputs" mkdir .build-outputs
echo Copiando APK para .build-outputs...
xcopy /Y "%APK_SRC%" ".build-outputs\" >nul
echo ==========================================================
echo APK (%BUILD_TYPE%) gerado com sucesso!
echo Destino: %APK_DEST%
echo ==========================================================

if "%SKIP_INSTALL%"=="true" (
    pause
    goto menu
)

:: Instalar no dispositivo
call :install_and_run
pause
goto menu


:: --- FUNCOES INTERNAS ---

:increment_version
:: Executar PowerShell para alterar versionCode e versionName no app/build.gradle.kts
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
goto :eof


:install_and_run
:: Detectar e instalar
echo Detectando dispositivos conectados via ADB...
where adb >nul 2>nul
if %errorlevel% neq 0 (
    echo ADB nao encontrado no PATH. Tentando usar o ANDROID_HOME...
    if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
        set "PATH=%ANDROID_HOME%\platform-tools;%PATH%"
    ) else (
        echo Erro: ADB nao encontrado.
        goto :eof
    )
)

set count=0
set "selected="
for /f "skip=1 tokens=1,2" %%a in ('adb devices 2^>nul') do (
    if "%%b"=="device" (
        set /a count+=1
        set "device!count!=%%a"
    )
)

if !count! EQU 0 (
    echo Nenhum celular/emulador detectado.
    echo Conecte o celular via USB e ative a Depuracao USB, ou inicie um emulador.
    goto :eof
)

if !count! EQU 1 (
    set "selected=!device1!"
    echo Dispositivo detectado: !selected!
    call :install_on_device !selected!
) else (
    echo Dispositivos conectados: !count!
    echo Instalando em todos os dispositivos...
    for /f "skip=1 tokens=1,2" %%a in ('adb devices 2^>nul') do (
        if "%%b"=="device" (
            echo Instalando no dispositivo: %%a
            call :install_on_device %%a
        )
    )
)
goto :eof

:install_on_device
set "current_device=%~1"
echo Instalando no dispositivo (!current_device!)...
:: Tentar instalacao
adb -s !current_device! install -r "%APK_DEST%"
if %errorlevel% neq 0 (
    echo Falha na instalacao direta. Tentando desinstalacao previa ^(incompatibilidade de assinatura^)...
    adb -s !current_device! uninstall com.aistudio.networktoolbox.kpqrsy
    adb -s !current_device! install -r "%APK_DEST%"
    if %errorlevel% neq 0 (
        echo Erro ao instalar o APK no dispositivo !current_device!.
    ) else (
        echo Instalacao realizada com sucesso no dispositivo !current_device!.
    )
) else (
    echo Instalacao realizada com sucesso no dispositivo !current_device!.
)

:: Abrir o aplicativo automaticamente
echo Abrindo o App (VYKnifeSwiss) no dispositivo !current_device!...
adb -s !current_device! shell am start -n com.aistudio.networktoolbox.kpqrsy/com.example.MainActivity
if %errorlevel% neq 0 (
    :: Fallback usando monkey caso o class path mude ou falhe
    echo Tentando abrir com monkey...
    adb -s !current_device! shell monkey -p com.aistudio.networktoolbox.kpqrsy -c android.intent.category.LAUNCHER 1
)

:: Redirecionamento de portas (opcional, igual ao CallNix)
adb -s !current_device! reverse tcp:8000 tcp:8000 >nul 2>&1
echo Porta 8000 redirecionada (PC -^> Celular) para dispositivo !current_device!
goto :eof
