Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-Docker {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $previous = $ErrorActionPreference
    try { $ErrorActionPreference = 'Continue'; $output = & docker @Arguments 2>&1; $code = $LASTEXITCODE }
    finally { $ErrorActionPreference = $previous }
    if ($code -ne 0) { throw "Docker falhou na operação '$($Arguments[0])'. Revise a configuração e o estado dos serviços. Código: $code" }
    return ($output | ForEach-Object { "$_" })
}

function Initialize-Compose {
    param([string]$Project, [string]$EnvFile, [string[]]$ComposeFiles)
    if ($Project -notmatch '^[a-z0-9][a-z0-9_-]+$') { throw 'Nome de projeto inválido.' }
    $script:ComposeArgs = @('compose', '-p', $Project, '--env-file', (Resolve-Path -LiteralPath $EnvFile).Path)
    foreach ($file in $ComposeFiles) { $script:ComposeArgs += @('-f', (Resolve-Path -LiteralPath $file).Path) }
}

function Invoke-Compose {
    param([string[]]$Arguments)
    Invoke-Docker -Arguments ($script:ComposeArgs + $Arguments)
}

function Get-ServiceContainer {
    param([string]$Service)
    $ids = @(Invoke-Compose -Arguments @('ps','-a','-q',$Service) | Where-Object { $_ -match '^[a-f0-9]{12,64}$' })
    if ($ids.Count -ne 1) { throw "Esperado um container para $Service." }
    return (Invoke-Docker -Arguments @('inspect',$ids[0]) | Out-String | ConvertFrom-Json)[0]
}

function Get-StorageVolume {
    param($Container)
    $volumes = @($Container.Mounts | Where-Object { $_.Destination -eq '/data' -and $_.Type -eq 'volume' })
    if ($volumes.Count -ne 1) { throw 'MinIO deve usar um volume Docker em /data.' }
    return $volumes[0].Name
}

function Get-DatabaseSetting {
    param($Container,[string]$Name)
    $line = @($Container.Config.Env | Where-Object { $_.StartsWith("${Name}=") })
    if ($line.Count -ne 1) { throw "Configuração $Name ausente." }
    return $line[0].Substring($Name.Length + 1)
}

function Write-Utf8Json {
    param([string]$Path,$Value)
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
}
