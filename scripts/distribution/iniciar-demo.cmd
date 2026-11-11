@echo off
cd /d "%~dp0"
if not exist tmp mkdir tmp
if not exist data mkdir data
set "TEMP=%~dp0tmp"
set "TMP=%~dp0tmp"
echo Abra http://localhost:8080 no Chrome ou Edge.
echo Mantenha esta janela aberta. Use Ctrl+C para encerrar.
java "-Djava.io.tmpdir=%~dp0tmp" -jar "%~dp0agenda-clara.jar" --spring.profiles.active=demo %*
if errorlevel 1 pause
