# Backup e recuperação do piloto

Procedimento da Fase 6, validado em Docker local com dados sintéticos. **Não houve
backup pré/pós de piloto real.** Evidências em [fase6-caua.md](../archive/fase6-caua.md).

## Preparação

- Responsável define janela, versão/commit aprovado, origem e diretório restrito
  **fora do repositório**. Preserve separadamente pré e pós; não sobrescreva o pré.
- Interrompa integrações, acessos administrativos de escrita ao PostgreSQL e
  qualquer escritor externo. O script para API e MinIO, mantendo PostgreSQL ativo
  para `pg_dump`. A consistência entre os dois depende dessa janela sem escritores.
- Requer Docker Compose com `config --format json`, PowerShell 5.1 ou superior,
  os serviços `api`, `postgres`, `minio` e storage em volume nomeado `/data`.
  Os scripts usam UTF-8 com BOM para mensagens legíveis no Windows PowerShell.
- Use o mesmo conjunto de arquivos Compose/overrides da origem. Arquivo `.env`
  preenchido fica restrito e ignorado; não passe credenciais como argumentos.
- Verifique espaço livre, health, logs e operador disponível para recuperar os
  serviços caso o processo/host seja interrompido. Mantenha log operacional restrito.
- Registre as configurações necessárias: `DATABASE_PASSWORD`, `JWT_SECRET`,
  `JWT_ACCESS_TTL`, `JWT_REFRESH_TTL`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET`,
  `PUBLIC_BASE_URL`, `PUBLIC_LINK_TTL`, `CORS_ALLOWED_ORIGINS`, `APP_BOOTSTRAP_*`,
  `SLOW_REQUEST_MS`, `DATABASE_CONNECTION_TIMEOUT_MS`, portas, imagens e volumes.
  Para execução fora do Compose, também `DATABASE_URL`, `DATABASE_USER`,
  `S3_ENDPOINT`, `S3_REGION`. Use os modelos `.env*.example` e um cofre separado
  para valores reais; o manifest não contém essas credenciais.

## Pré-piloto

Exemplo PowerShell (substitua caminhos e projeto pelo ambiente aprovado):

```powershell
$origem = 'garagem-piloto'
$ambiente = 'C:\Operacao\garagem\piloto.env'
$composes = @('compose.yml', 'C:\Operacao\garagem\piloto-compose.yml')
$versao = 'COMMIT-OU-IMAGEM-APROVADA'
& .\scripts\backup-piloto.ps1 -Project $origem -EnvFile $ambiente `
  -ComposeFiles $composes -Destination 'D:\Backups\Garagem' -Stage pre `
  -Operator 'NOME-DO-RESPONSAVEL' -ApplicationVersion $versao -MaintenanceConfirmed
```

O script cria `garagem-piloto-pre-<UTC>` contendo:

| Arquivo | Conteúdo |
|---|---|
| `postgres.dump` | Schema e dados, formato custom `pg_dump -Fc`, sem owner/ACL |
| `minio.tar.gz` | Volume inteiro a frio, objetos e metadata/configuração MinIO |
| `manifest.json` | UTC, operador, origem, versão declarada, imagem API, imagens PG/MinIO, versões Flyway, bytes e SHA-256 |

O dump é gravado dentro do container e copiado por `docker cp`; não passa por
pipeline binário PowerShell. O tar usa volume de origem somente leitura, MinIO
parado e helper sem rede. API e MinIO são reiniciados em `finally`. Em falha,
`FAILED.json` invalida o conjunto; confirme manualmente a recuperação dos serviços.
Interrupção forçada/queda do host pode impedir o `finally`: não libere o sistema
apenas porque o terminal fechou. Verifique manifest, health e restore.

Faça restore isolado e aceite somente após os critérios abaixo. Registre horário,
responsável, checksums e evidência. **Liberar o piloto só depois da aceitação.**

## Restore isolado

Crie um override com portas livres e uma imagem API já construída da mesma versão:

```yaml
services:
  api:
    image: garagem-piloto-api:VERSAO-APROVADA
    ports: !override ["127.0.0.1:18087:8080"]
  postgres:
    ports: !override ["127.0.0.1:15437:5432"]
  minio:
    ports: !override ["127.0.0.1:19010:9000", "127.0.0.1:19011:9001"]
```

Use um nome de projeto **novo**, sem containers ou volumes anteriores; não use
`container_name`, mounts externos, bind mounts de dados ou nomes fixos de volumes.
O script exige volumes com prefixo do projeto para PostgreSQL/MinIO. Confira também
todo override e os mounts dos demais serviços. Preserve as imagens PG/MinIO do
manifest: este procedimento físico de MinIO não é uma migração entre versões.

```powershell
$backup = 'D:\Backups\Garagem\garagem-piloto-pre-TIMESTAMP'
& .\scripts\restore-piloto.ps1 -Project 'garagem-restore-validacao-001' `
  -EnvFile $ambiente -ComposeFiles @('compose.yml','C:\Operacao\garagem\restore.yml') `
  -BackupDirectory $backup -IsolatedTargetConfirmed
```

A validação de tamanho/SHA-256 ocorre antes de criar containers. O script recusa
origem, destino ocupado, backup incompleto, checksum divergente e imagens de
PG/MinIO diferentes. Ele restaura primeiro storage frio e banco vazio, depois sobe
a API sem build. Não remove dados nem tenta limpar/reutilizar destino parcial:
investigue a falha e use outro projeto novo. Nunca faça restore em cima da origem.

Após subir, aguarde inicialização e confira:

1. Health e readiness 200/UP; Flyway sem erro, versões iguais ao manifest.
2. Login com usuário restaurado; logout/login e nova sessão.
3. Oficina/usuário, cliente, veículo, OS e vínculos/tenant preservados.
4. Checklist, diagnóstico, versões do orçamento, decisão pública e timeline.
5. Oportunidades, contatos, resultados, auditoria e total recuperado.
6. Foto listada no banco, download autenticado e SHA-256 igual ao original;
   acesso anônimo ao objeto negado. Não basta existir uma linha de metadata.
7. Contagens e marcos operacionais registrados na origem. Nunca escreva no
   restore esperando que a alteração seja refletida na origem.

## Pós-piloto

Encerre a janela operacional e registre incidentes, contagens e indicadores finais.
Repita o procedimento com `-Stage pos`, nova pasta e novo projeto de restore.
Compare o estado recuperado com o encerramento: novos registros, status, contatos,
resultados, valores recuperados e fotos. Preserve o backup pré-piloto. Registre
operador, UTC, versão, checksums e aceite do restore de ambos separadamente.

## Proteção e retenção

Dump e storage contêm dados pessoais, hashes/tokens persistidos e possivelmente
configuração interna sensível do MinIO. Não são arquivos públicos. Restrinja ACLs,
use armazenamento criptografado e cópia fora do host conforme política do piloto.
Isso é responsabilidade operacional: os scripts não configuram ACL, criptografia,
agendamento, retenção nem cópia remota. SHA-256 detecta corrupção; não autentica
um manifest alterado junto com o backup. Use somente conjuntos de origem confiável.

`.tools/`, `backups/`, `*.dump` e `garagem-piloto-*/` estão ignorados. Isso não protege
um arquivo renomeado fora desses padrões: confira `git status` e nunca force adição
de backups, logs privados ou `.env`. Não há exclusão automática nem `down -v`.
