$ErrorActionPreference = 'Stop'
$script:ProjectRoot = Split-Path -Parent $PSScriptRoot
function Initialize-ProjectEnvironment {
    Set-Location -LiteralPath $script:ProjectRoot
    foreach ($folder in @('.cache/tmp', '.cache/npm', '.cache/maven', '.cache/m2', 'data')) {
        [System.IO.Directory]::CreateDirectory((Join-Path $script:ProjectRoot $folder)) | Out-Null
    }
    $env:TEMP = Join-Path $script:ProjectRoot '.cache/tmp'
    $env:TMP = $env:TEMP
    $env:npm_config_cache = Join-Path $script:ProjectRoot '.cache/npm'
    $env:MAVEN_USER_HOME = Join-Path $script:ProjectRoot '.cache/maven'
    $env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $script:ProjectRoot '.cache/browsers'
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Instale um JDK 21 ou mais recente e adicione-o ao PATH. Consulte o README.' }
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Instale o Node.js 24.18 ou mais recente da linha 24 LTS. Consulte o README.' }
    & node -e "const [major,minor]=process.versions.node.split('.').map(Number); if(!((major===24&&minor>=18)||major>=26)){console.error('Use Node.js 24.18+ (linha 24) ou 26+.');process.exit(1)}"
    if ($LASTEXITCODE -ne 0) { throw 'Versão do Node.js incompatível.' }
}
function Invoke-WebDependencies {
    $lock = Join-Path $script:ProjectRoot 'frontend/package-lock.json'
    $stamp = Join-Path $script:ProjectRoot '.cache/web-dependencies.sha256'
    $hash = (Get-FileHash -LiteralPath $lock -Algorithm SHA256).Hash
    if (-not (Test-Path -LiteralPath (Join-Path $script:ProjectRoot 'frontend/node_modules')) -or -not (Test-Path -LiteralPath $stamp) -or (Get-Content -LiteralPath $stamp -Raw).Trim() -ne $hash) {
        Push-Location (Join-Path $script:ProjectRoot 'frontend')
        try { & npm.cmd ci --prefer-offline --no-audit --no-fund; if ($LASTEXITCODE -ne 0) { throw 'Falha ao baixar as dependências da interface.' } }
        finally { Pop-Location }
        [System.IO.File]::WriteAllText($stamp, $hash)
    }
}
function Invoke-ProjectMaven {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArguments)
    & (Join-Path $script:ProjectRoot 'mvnw.cmd') '-Dmaven.repo.local=.cache/m2' -B -ntp @MavenArguments
    if ($LASTEXITCODE -ne 0) { throw 'A verificação Java falhou. Consulte a mensagem acima.' }
}
