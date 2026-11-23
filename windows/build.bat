@echo off
REM Build VYChat.exe for Windows

echo Installing dependencies...
python -m pip install -r requirements.txt
python -m pip install pyinstaller

echo Building executable...
pyinstaller --onefile --windowed --name VYChat --clean chat.py

echo Build complete. Check the dist\ folder.
pause
