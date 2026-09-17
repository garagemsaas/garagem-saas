[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Project,
    [Parameter(Mandatory)][string]$EnvFile,
    [Parameter(Mandatory)][string[]]$ComposeFiles,
    [Parameter(Mandatory)][string]$Destination,
    [Parameter(Mandatory)][ValidateSet('pre','pos')][string]$Stage,
    [Parameter(Mandatory)][string]$Operator,
    [Parameter(Mandatory)][string]$ApplicationVersion,
    [Parameter(Mandatory)][switch]$MaintenanceConfirmed
)
. "$PSScriptRoot/piloto-common.ps1"
if (-not $MaintenanceConfirmed) { throw 'Confirme janela de manutenção sem escritores externos.' }
Initialize-Compose $Project $EnvFile $ComposeFiles
$api = Get-ServiceContainer 'api'
$pg = Get-ServiceContainer 'postgres'
$minio = Get-ServiceContainer 'minio'
if (-not ($api.State.Running -and $pg.State.Running -and $minio.State.Running)) { throw 'A origem deve estar em execução antes do backup.' }
$volume = Get-StorageVolume $minio
$db = Get-DatabaseSetting $pg 'POSTGRES_DB'
$user = Get-DatabaseSetting $pg 'POSTGRES_USER'
$stamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
$root = [IO.Path]::GetFullPath($Destination)
[IO.Directory]::CreateDirectory($root) | Out-Null
$folder = Join-Path $root "garagem-piloto-$Stage-$stamp"
if (Test-Path -LiteralPath $folder) { throw 'Backup já existe. Não será sobrescrito.' }
[IO.Directory]::CreateDirectory($folder) | Out-Null
$temp = "/tmp/garagem-$stamp.dump"
$stopped = $false
try {
    Write-Host "Pausando API e MinIO do projeto $Project para cópia consistente."
    $stopped = $true
    Invoke-Compose -Arguments @('stop','-t','30','api','minio') | Out-Null
    if ((Get-ServiceContainer 'api').State.Running -or (Get-ServiceContainer 'minio').State.Running) { throw 'Não foi possível interromper escritores.' }
    Invoke-Docker -Arguments @('exec',$pg.Id,'pg_dump','-U',$user,'-d',$db,'-Fc','--no-owner','--no-privileges','-f',$temp) | Out-Null
    Invoke-Docker -Arguments @('cp',"$($pg.Id):$temp",(Join-Path $folder 'postgres.dump')) | Out-Null
    # Cópia física a frio: inclui objetos, metadata e configuração MinIO. Restaurar com a mesma imagem.
    Invoke-Docker -Arguments @('run','--rm','--network','none','--mount',"type=volume,source=$volume,target=/data,readonly",'--mount',"type=bind,source=$folder,target=/backup",$pg.Config.Image,'tar','-czf','/backup/minio.tar.gz','-C','/data','.') | Out-Null
    $schema = (Invoke-Docker -Arguments @('exec',$pg.Id,'psql','-U',$user,'-d',$db,'-At','-c',"select string_agg(version,',' order by installed_rank) from flyway_schema_history where success") | Out-String).Trim()
    $files = foreach ($name in @('postgres.dump','minio.tar.gz')) {
        $file = Get-Item -LiteralPath (Join-Path $folder $name)
        if ($file.Length -eq 0) { throw 'Arquivo de backup vazio.' }
        @{ name=$name; bytes=$file.Length; sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash }
    }
    Write-Utf8Json (Join-Path $folder 'manifest.json') ([ordered]@{
        format=1; stage=$Stage; createdAtUtc=[DateTime]::UtcNow.ToString('o'); sourceProject=$Project;
        operator=$Operator; applicationVersion=$ApplicationVersion; schemaVersions=$schema;
        postgresImage=$pg.Config.Image; minioImage=$minio.Config.Image; apiImageId=$api.Image;
        consistency='API e MinIO parados; nenhum escritor externo autorizado'; files=@($files)
    })
    Write-Host "Backup criado: $folder. Restore isolado ainda é obrigatório."
} catch {
    Write-Utf8Json (Join-Path $folder 'FAILED.json') @{ failedAtUtc=[DateTime]::UtcNow.ToString('o'); message='Backup incompleto; não restaurar.' }
    throw
} finally {
    # Apenas o arquivo temporário criado por esta execução, no container identificado.
    try { Invoke-Docker -Arguments @('exec',$pg.Id,'rm','-f',$temp) | Out-Null }
    finally { if ($stopped) { Invoke-Compose -Arguments @('start','minio','api') | Out-Null } }
}
