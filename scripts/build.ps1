param([switch]$SkipTests)
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-ProjectEnvironment
$existingJar = Join-Path $script:ProjectRoot 'target/agenda-clara-0.2.1.jar'
if (Test-Path -LiteralPath $existingJar) {
    try { $handle = [System.IO.File]::Open($existingJar, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None); $handle.Dispose() }
    catch { throw 'O aplicativo está em execução. Encerre-o com Ctrl+C antes de recompilar.' }
}
Invoke-WebDependencies
Push-Location (Join-Path $script:ProjectRoot 'frontend')
try {
    if (-not $SkipTests) { & npm.cmd test; if ($LASTEXITCODE -ne 0) { throw 'Os testes da interface falharam.' } }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'A compilação da interface falhou.' }
} finally { Pop-Location }
if ($SkipTests) { Invoke-ProjectMaven '-DskipTests' 'clean' 'package' } else { Invoke-ProjectMaven 'clean' 'verify' }
Write-Host 'Pronto: target/agenda-clara-0.2.1.jar'
