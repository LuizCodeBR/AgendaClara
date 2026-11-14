param(
    [ValidateSet('backup','report')][string]$Action = 'report',
    [string]$PrivateDirectory = '',
    [string]$WorkDirectory = '',
    [string]$ProviderInput = ''
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $PrivateDirectory) { $PrivateDirectory = Join-Path $root '.cache/neon-private' }
if (-not $WorkDirectory) { $WorkDirectory = Join-Path $root '.cache/neon-operations' }
$explicitProviderInput = -not [string]::IsNullOrEmpty($ProviderInput)
if (-not $ProviderInput) { $ProviderInput = Join-Path $PrivateDirectory 'provider-usage.json' }
function Read-PrivateFile([string]$path, [long]$maximum = 4096) {
    $item = Get-Item -LiteralPath $path -Force
    if ($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Arquivo privado inválido.' }
    for ($parent = $item.Directory; $parent; $parent = $parent.Parent) {
        if ($parent.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Pasta privada redirecionada.' }
    }
    $stream = [IO.File]::Open($item.FullName, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
    try {
        $bytes = New-Object byte[] ($maximum + 1)
        $total = 0
        while ($total -lt $bytes.Length) {
            $count = $stream.Read($bytes, $total, $bytes.Length - $total)
            if ($count -eq 0) { break }
            $total += $count
        }
        if ($total -gt $maximum) { throw 'Arquivo privado maior que o limite.' }
        $text = [Text.UTF8Encoding]::new($false, $true).GetString($bytes, 0, $total)
        if ($text.StartsWith([string][char]0xFEFF, [StringComparison]::Ordinal)) { $text = $text.Substring(1) }
        return $text.TrimEnd([char[]]@(13,10))
    }
    finally { $stream.Dispose(); if ($bytes) { [Array]::Clear($bytes, 0, $bytes.Length) } }
}

$keys = @('DB_URL','DB_USER','DB_PASSWORD','MAIL_KEY_FILE','MAIL_ENCRYPTION_KEY','AGENDA_BACKUP_PASSWORD','PG_DUMP_PATH','TEMP','TMP','NEON_PROJECT_ID','RENDER_WORKSPACE_ID')
$previous = @{}
foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$exitCode = 1
$consoleEncoding = [Console]::OutputEncoding
try {
    $config = Read-PrivateFile (Join-Path $PrivateDirectory 'connection.json') | ConvertFrom-Json
    if (-not $config.url -or -not $config.user -or -not $config.password) { throw 'Configuração incompleta.' }
    $env:DB_URL = $config.url
    $env:DB_USER = $config.user
    $env:DB_PASSWORD = $config.password
    $env:MAIL_KEY_FILE = Join-Path $PrivateDirectory 'mail-outbox.key'
    $env:MAIL_ENCRYPTION_KEY = ''
    if ($Action -eq 'backup') { $env:AGENDA_BACKUP_PASSWORD = Read-PrivateFile (Join-Path $PrivateDirectory 'backup-password.txt') }
    $portable = Join-Path $root '.cache/postgres-tools/pgsql/bin/pg_dump.exe'
    if (Test-Path -LiteralPath $portable) { $env:PG_DUMP_PATH = $portable }
    $resources = Join-Path $PrivateDirectory 'resources.json'
    if (Test-Path -LiteralPath $resources) {
        $ids = Read-PrivateFile $resources | ConvertFrom-Json
        $env:NEON_PROJECT_ID = $ids.neonProjectId
        $env:RENDER_WORKSPACE_ID = $ids.renderWorkspaceId
    }
    $env:TEMP = Join-Path $root '.cache/tmp'
    $env:TMP = $env:TEMP
    [IO.Directory]::CreateDirectory($env:TEMP) | Out-Null
    $jar = Join-Path $root 'target/agenda-clara-0.11.0.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Compile a versão 0.11.0 antes de executar.' }
    $arguments = @('-Xmx512m', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', "-Djava.io.tmpdir=$env:TEMP", '-jar', $jar, "--operations=$Action", '--engine=postgres', "--work-dir=$WorkDirectory")
    if ($explicitProviderInput -or (Test-Path -LiteralPath $ProviderInput)) { $arguments += "--provider-input=$ProviderInput" }
    [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
    & java @arguments
    $exitCode = $LASTEXITCODE
}
catch { Write-Output 'ERRO: rotina Neon não concluída. Confira os arquivos privados e a compilação no D:. Nenhuma credencial foi exibida.' }
finally {
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    [Console]::OutputEncoding = $consoleEncoding
    $config = $null
}
exit $exitCode
