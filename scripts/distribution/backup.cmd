@echo off
cd /d "%~dp0"
if not exist tmp mkdir tmp
set "TEMP=%~dp0tmp"
set "TMP=%~dp0tmp"
if "%~1"=="" (
  java -Xmx512m "-Djava.io.tmpdir=%~dp0tmp" -jar "%~dp0agenda-clara.jar" --backup=create
) else (
  java -Xmx512m "-Djava.io.tmpdir=%~dp0tmp" -jar "%~dp0agenda-clara.jar" %*
)
exit /b %errorlevel%
