#!/bin/bash
# Build standalone executable for Linux/macOS
set -e

echo "Installing dependencies..."
python3 -m pip install -r requirements.txt
python3 -m pip install pyinstaller

echo "Building executable..."
pyinstaller --onefile --name VYChat --clean chat.py

echo "Build complete. Check the dist/ folder."
