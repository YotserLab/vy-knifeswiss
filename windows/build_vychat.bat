@echo off
REM Build VYChat.exe for Windows

setlocal enabledelayedexpansion

set "BUILD_DIR=%TEMP%\VYChatBuild"
set "SPEC_DIR=%BUILD_DIR%\spec"
set "WORK_DIR=%BUILD_DIR%\work"
set "DIST_DIR=%BUILD_DIR%\dist"
set "FINAL_DIR=%~dp0dist"
set "SOURCE_DIR=%~dp0windows"

echo Preparing build directories...
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
if not exist "%SPEC_DIR%" mkdir "%SPEC_DIR%"
if not exist "%WORK_DIR%" mkdir "%WORK_DIR%"
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
if not exist "%FINAL_DIR%" mkdir "%FINAL_DIR%"

copy /Y "%SOURCE_DIR%\chat.py" "%BUILD_DIR%\chat.py" >nul
copy /Y "%SOURCE_DIR%\crypto.py" "%BUILD_DIR%\crypto.py" >nul
copy /Y "%SOURCE_DIR%\file_server.py" "%BUILD_DIR%\file_server.py" >nul
copy /Y "%SOURCE_DIR%\file_transfer.py" "%BUILD_DIR%\file_transfer.py" >nul
copy /Y "%SOURCE_DIR%\adb_tools.py" "%BUILD_DIR%\adb_tools.py" >nul
copy /Y "%SOURCE_DIR%\adb_window.py" "%BUILD_DIR%\adb_window.py" >nul
copy /Y "%SOURCE_DIR%\version.txt" "%BUILD_DIR%\version.txt" >nul
copy /Y "%SOURCE_DIR%\requirements.txt" "%BUILD_DIR%\requirements.txt" >nul

echo Installing dependencies...
python -m pip install -r "%BUILD_DIR%\requirements.txt"
python -m pip install pyinstaller

echo Building executable in temporary local directory...
cd /d "%BUILD_DIR%"
pyinstaller --onefile --windowed --name VYChat --clean --specpath "%SPEC_DIR%" --workpath "%WORK_DIR%" --distpath "%DIST_DIR%" chat.py

echo Copying executable to project folder...
copy /Y "%DIST_DIR%\VYChat.exe" "%FINAL_DIR%\VYChat.exe" >nul

echo Build complete. Executable: %FINAL_DIR%\VYChat.exe
pause
