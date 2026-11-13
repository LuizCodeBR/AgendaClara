param([switch]$SkipBuild, [ValidateRange(1024,65535)][int]$Port = 8080)
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-ProjectEnvironment
if (-not $SkipBuild) { & (Join-Path $PSScriptRoot 'build.ps1') }
$jar = Join-Path $script:ProjectRoot 'target/agenda-clara-0.8.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Execute scripts/build.ps1 antes de usar -SkipBuild.' }
Write-Host ''
Write-Host ("Abra http://localhost:" + $Port + " no navegador. Use Ctrl+C para encerrar.")
Write-Host 'Para usar como aplicativo: no Chrome ou Edge, escolha Instalar Agenda Clara.'
Write-Host ''
& java ("-Djava.io.tmpdir=" + $env:TEMP) -jar $jar '--spring.profiles.active=demo' ("--server.port=" + $Port)
exit $LASTEXITCODE
