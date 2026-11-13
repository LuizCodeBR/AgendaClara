@echo off
cd /d "%~dp0"
if not exist .cache\tmp mkdir .cache\tmp
set "TEMP=%~dp0.cache\tmp"
set "TMP=%~dp0.cache\tmp"
if "%~1"=="" (
  java -Xmx512m "-Djava.io.tmpdir=%~dp0.cache\tmp" -jar "%~dp0target\agenda-clara-0.8.0.jar" --operations=report
) else (
  java -Xmx512m "-Djava.io.tmpdir=%~dp0.cache\tmp" -jar "%~dp0target\agenda-clara-0.8.0.jar" %*
)
exit /b %errorlevel%
