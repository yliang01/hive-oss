@echo off
setlocal
set "APP_DIR=%~dp0"
cd /d "%APP_DIR%"
java -jar "%APP_DIR%hive-oss.jar"
pause
