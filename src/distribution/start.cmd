@echo off
setlocal EnableExtensions
set "APP_DIR=%~dp0"
cd /d "%APP_DIR%"
where java >nul 2>nul || (
  echo ERROR: Java 21 is required and must be available on PATH.
  exit /b 1
)
java -jar "%APP_DIR%hive-oss.jar" %*
exit /b %errorlevel%
