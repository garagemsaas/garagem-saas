[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Project,
    [Parameter(Mandatory)][string]$EnvFile,
    [Parameter(Mandatory)][string[]]$ComposeFiles,
    [Parameter(Mandatory)][string]$BackupDirectory,
    [Parameter(Mandatory)][switch]$IsolatedTargetConfirmed
)
. "$PSScriptRoot/piloto-common.ps1"
if (-not $IsolatedTargetConfirmed) { throw 'Restore exige destino isolado e novo.' }
Initialize-Compose $Project $EnvFile $ComposeFiles
$folder=(Resolve-Path -LiteralPath $BackupDirectory).Path
if (Test-Path -LiteralPath (Join-Path $folder 'FAILED.json')) { throw 'Backup marcado como incompleto.' }
$manifest = Get-Content -LiteralPath (Join-Path $folder 'manifest.json') -Raw | ConvertFrom-Json
if ($manifest.format -ne 1 -or $manifest.sourceProject -eq $Project) { throw 'Formato inválido ou tentativa de restaurar na origem.' }
if (@(Invoke-Docker -Arguments @('ps','-aq','--filter',"label=com.docker.compose.project=$Project") | Where-Object { $_ }).Count -gt 0 -or
    @(Invoke-Docker -Arguments @('volume','ls','-q','--filter',"label=com.docker.compose.project=$Project") | Where-Object { $_ }).Count -gt 0) { throw 'Projeto destino já tem containers/volumes. Use um projeto NOVO; nada será removido.' }
foreach ($name in @('postgres.dump','minio.tar.gz')) {
    $entry=@($manifest.files | Where-Object { $_.name -eq $name })
    $file=Get-Item -LiteralPath (Join-Path $folder $name)
    if ($entry.Count -ne 1 -or $file.Length -ne $entry[0].bytes -or
        (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash -ne $entry[0].sha256) { throw "Checksum/tamanho inválido: $name" }
}
# Não aceitar mounts externos ou nomes fixos: poderiam apontar para dados de outra stack.
$config=Invoke-Compose -Arguments @('config','--format','json') | Out-String | ConvertFrom-Json
$existingVolumes = @(Invoke-Docker -Arguments @('volume','ls','--format','{{.Name}}'))
foreach ($service in @('postgres','minio')) {
    foreach ($mount in $config.services.$service.volumes) {
        if ($mount.type -ne 'volume' -or $config.volumes.($mount.source).name -ne "${Project}_$($mount.source)") { throw 'Destino deve usar apenas volumes nomeados próprios do projeto.' }
        if ($existingVolumes -contains $config.volumes.($mount.source).name) { throw 'Volume destino já existe, mesmo sem label Compose. Use um projeto NOVO.' }
    }
}
if ($config.services.minio.image -ne $manifest.minioImage -or $config.services.postgres.image -ne $manifest.postgresImage) { throw 'Use as mesmas imagens PostgreSQL/MinIO do backup nesta restauração.' }
Invoke-Compose -Arguments @('create','postgres','minio') | Out-Null
$pg=Get-ServiceContainer 'postgres';$minio=Get-ServiceContainer 'minio'
$volume=Get-StorageVolume $minio
$db=Get-DatabaseSetting $pg 'POSTGRES_DB';$user=Get-DatabaseSetting $pg 'POSTGRES_USER'
Invoke-Docker -Arguments @('run','--rm','--network','none','--mount',"type=volume,source=$volume,target=/data",'--mount',"type=bind,source=$folder,target=/backup,readonly",$pg.Config.Image,'tar','-xzf','/backup/minio.tar.gz','-C','/data') | Out-Null
Invoke-Compose -Arguments @('start','postgres') | Out-Null
$ready=$false
for($i=0;$i -lt 60;$i++) {
    # O bootstrap da imagem usa apenas socket Unix; TCP só abre no servidor definitivo.
    & docker exec $pg.Id pg_isready -h 127.0.0.1 -U $user -d $db *> $null
    if($LASTEXITCODE -eq 0) {$ready=$true;break}
    Start-Sleep -Seconds 1
}
if(-not $ready) {throw 'PostgreSQL destino não iniciou.'}
Invoke-Docker -Arguments @('cp',(Join-Path $folder 'postgres.dump'),"$($pg.Id):/tmp/restore-piloto.dump") | Out-Null
try {
    Invoke-Docker -Arguments @('exec',$pg.Id,'pg_restore','--exit-on-error','--no-owner','--no-privileges','-U',$user,'-d',$db,'/tmp/restore-piloto.dump') | Out-Null
} finally { Invoke-Docker -Arguments @('exec',$pg.Id,'rm','-f','/tmp/restore-piloto.dump') | Out-Null }
Invoke-Compose -Arguments @('up','-d','--no-build') | Out-Null
Write-Host "Restore em $Project concluído. Confirme health, Flyway, login, relações e download das fotos antes de aceitar."
