@echo off
setlocal
set "APP_DIR=%~dp0"
cd /d "%APP_DIR%"
java -jar "%APP_DIR%hive-oss.jar" ^
  --spring.config.additional-location="file:%APP_DIR%config/external-secret.yml" ^
  --hive.imageCacheDir="%APP_DIR%data/image-cache/" ^
  --hive.imageCacheTtlDays=7 ^
  --hive.downloadDir="%APP_DIR%data/download/" ^
pause
