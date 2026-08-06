@echo off
setlocal

cd /d "%~dp0"

echo [build] Closing running Minecraft/javaw processes...
taskkill /F /T /IM javaw.exe >nul 2>nul

echo [build] Closing old injector process if it is still open...
taskkill /F /T /IM Vape421Injector.exe >nul 2>nul

echo [build] Building injection bundle...
call gradlew.bat prepareInjectionBundle
if errorlevel 1 (
    echo [build] Failed.
    pause
    exit /b 1
)

echo [build] Done: build\injection
pause
