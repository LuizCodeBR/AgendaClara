param([string]$OutputDirectory = 'D:\Projetos\Entregas')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'target/agenda-clara-0.1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Compile o projeto antes de empacotar.' }
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git é necessário para selecionar os arquivos de código-fonte.' }
$destination = Join-Path ([System.IO.Path]::GetFullPath($OutputDirectory)) ('AgendaClara-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
[System.IO.Directory]::CreateDirectory($destination) | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$sourceZip = Join-Path $destination 'AgendaClara-0.1.0-fonte.zip'
$archive = [System.IO.Compression.ZipFile]::Open($sourceZip,[System.IO.Compression.ZipArchiveMode]::Create)
try {
    $files = & git -C $root ls-files -co --exclude-standard
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao selecionar os arquivos do projeto.' }
    foreach ($relative in ($files | Sort-Object -Unique)) {
        $source = Join-Path $root $relative
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$source,('AgendaClara/' + $relative.Replace('\','/')),[System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose() }
$demoZip = Join-Path $destination 'AgendaClara-0.1.0-demo.zip'
$archive = [System.IO.Compression.ZipFile]::Open($demoZip,[System.IO.Compression.ZipArchiveMode]::Create)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$jar,'AgendaClara-demo/agenda-clara-0.1.0.jar',[System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    foreach ($name in @('iniciar-demo.cmd','iniciar-demo.sh','LEIA-ME.md')) {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $PSScriptRoot ('distribution/' + $name)),('AgendaClara-demo/' + $name),[System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose() }
Get-Item -LiteralPath $sourceZip,$demoZip | Select-Object FullName,Length
Get-FileHash -LiteralPath $sourceZip,$demoZip -Algorithm SHA256 | Select-Object Path,Hash
