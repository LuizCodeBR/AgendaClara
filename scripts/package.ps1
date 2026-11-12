param([string]$OutputDirectory = 'D:\Projetos\Entregas')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'target/agenda-clara-0.2.1.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Compile o projeto antes de empacotar.' }
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git é necessário para selecionar os arquivos de código-fonte.' }
$destination = Join-Path ([System.IO.Path]::GetFullPath($OutputDirectory)) ('AgendaClara-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
[System.IO.Directory]::CreateDirectory($destination) | Out-Null
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$sourceZip = Join-Path $destination 'AgendaClara-0.2.1-fonte.zip'
$archive = [System.IO.Compression.ZipFile]::Open($sourceZip,[System.IO.Compression.ZipArchiveMode]::Create)
try {
    $files = & git -C $root ls-files -co --exclude-standard
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao selecionar os arquivos do projeto.' }
    foreach ($relative in ($files | Sort-Object -Unique)) {
        $source = Join-Path $root $relative
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$source,('AgendaClara/' + $relative.Replace('\','/')),[System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose() }
$demoZip = Join-Path $destination 'AgendaClara-0.2.1-demo.zip'
$archive = [System.IO.Compression.ZipFile]::Open($demoZip,[System.IO.Compression.ZipArchiveMode]::Create)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$jar,'AgendaClara-demo/agenda-clara.jar',[System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    foreach ($name in @('iniciar-demo.cmd','iniciar-demo.sh','LEIA-ME.md')) {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,(Join-Path $PSScriptRoot ('distribution/' + $name)),('AgendaClara-demo/' + $name),[System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose() }
$artifacts = foreach ($filePath in @($sourceZip, $demoZip)) {
    $item = Get-Item -LiteralPath $filePath
    $digest = Get-FileHash -LiteralPath $filePath -Algorithm SHA256
    [pscustomobject]@{ Path = $item.FullName; Length = $item.Length; Sha256 = $digest.Hash }
}
$artifacts | ConvertTo-Json
