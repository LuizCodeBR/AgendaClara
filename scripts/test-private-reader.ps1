$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $PSScriptRoot 'operacao-neon.ps1'
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'Falha de sintaxe no adaptador.' }
$definition = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Read-PrivateFile' }, $true)
if (-not $definition) { throw 'Leitor não encontrado.' }
# Execute only the tracked function definition; never run the adapter or read its real credentials.
. ([scriptblock]::Create($definition.Extent.Text))
$tempRoot = [IO.Path]::GetFullPath((Join-Path $root '.cache/tmp'))
[IO.Directory]::CreateDirectory($tempRoot) | Out-Null
$directory = Join-Path $tempRoot ('private-reader-' + [guid]::NewGuid())
[IO.Directory]::CreateDirectory($directory) | Out-Null
try {
    $path = Join-Path $directory 'input.txt'
    $json = '{"value":"conteúdo fictício"}'
    foreach ($bom in @($false, $true)) {
        [IO.File]::WriteAllText($path, $json, [Text.UTF8Encoding]::new($bom))
        if ((Read-PrivateFile $path) -cne $json) { throw 'JSON com ou sem BOM alterado pelo leitor.' }
    }
    $password = '  senha ficticia com espacos  '
    [IO.File]::WriteAllText($path, $password, [Text.UTF8Encoding]::new($false))
    if ((Read-PrivateFile $path) -cne $password) { throw 'Espaços da senha foram removidos.' }
    [IO.File]::WriteAllText($path, ('X' * 4096), [Text.UTF8Encoding]::new($false))
    if ((Read-PrivateFile $path).Length -ne 4096) { throw 'Limite exato de leitura incorreto.' }
    foreach ($bytes in @([Text.Encoding]::UTF8.GetBytes('X' * 4097), [byte[]]@(0xC3, 0x28))) {
        [IO.File]::WriteAllBytes($path, $bytes)
        $rejected = $false
        try { Read-PrivateFile $path | Out-Null } catch { $rejected = $true }
        if (-not $rejected) { throw 'Arquivo excessivo ou UTF-8 inválido foi aceito.' }
    }
    if ([Environment]::OSVersion.Platform -eq 'Win32NT') {
        [IO.File]::WriteAllText($path, $json, [Text.UTF8Encoding]::new($false))
        $writer = [IO.File]::Open($path, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::ReadWrite)
        try {
            $rejected = $false
            try { Read-PrivateFile $path | Out-Null } catch { $rejected = $true }
            if (-not $rejected) { throw 'Leitor aceitou arquivo aberto para alteração concorrente.' }
        } finally { $writer.Dispose() }
    }
    Write-Output 'OK: leitor privado preserva JSON/BOM e espaços, limita a leitura, recusa UTF-8 inválido e protege contra alteração concorrente no Windows.'
}
finally {
    $resolved = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $directory).ProviderPath)
    if (-not $resolved.StartsWith($tempRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        ([IO.File]::GetAttributes($resolved) -band [IO.FileAttributes]::ReparsePoint)) { throw 'Destino de limpeza de teste inválido.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
