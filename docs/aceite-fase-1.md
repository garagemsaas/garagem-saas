# Aceite e verificações — 14/09/2026

A Fase 1 ainda não está concluída como produto: o backend está implementado e validado localmente; interface, validação completa em containers e staging estão pendentes. Nenhuma funcionalidade das fases seguintes foi iniciada.

## Verificações realizadas

| Verificação | Resultado |
|---|---|
| Compilação e empacotamento com Java 25.0.4.1 e Maven 3.9.11 | Passou |
| JUnit 5 — regras de status e arredondamento | 4 testes, zero falhas/erros/ignorados |
| Integração — PostgreSQL 17.11 real | 10 testes, zero falhas/erros/ignorados |
| Spotless/Google Java Format | Passou |
| Flyway V1 e V2 + Hibernate `ddl-auto=validate` | Passou |
| Aplicar V1/V2 e reverter V2/V1 em schema temporário vazio | Passou; schema removido sem CASCADE |
| `docker compose --env-file .env.example config --quiet` | Passou |
| Sintaxe do Maven Wrapper para shell | Passou |
| Maven Wrapper executando Maven/Java corretos | Passou com cache local previamente preenchido |
| Integridade do Maven baixado | SHA-512 conferido com Maven Central; SHA-256 fixado no wrapper |
| Docker build e execução de containers | Pendente: virtualização indisponível nesta máquina |
| PostgreSQL/MinIO via Testcontainers | Configurado no CI; execução nesse modo ainda pendente |

Comando executado na raiz com `JAVA_HOME` apontando ao JDK local, DEBUG=false e `TEST_DATABASE_*` apontando ao PostgreSQL descartável em localhost:55432:

```powershell
.tools/apache-maven-3.9.11/bin/mvn.cmd -B -ntp -f backend/pom.xml '-Dmaven.repo.local=C:\Projetos\garagem-saas\.tools\m2' spotless:apply verify
```

Relatórios locais: `backend/target/surefire-reports` e `backend/target/failsafe-reports`. O CI publica esses relatórios como artifact. Binários e logs em `.tools` não são versionados. O servidor PostgreSQL portátil foi parado após a validação.

## Cobertura da integração

1. Cadastros, normalização de placa, revisões concorrentes e isolamento de leitura/escrita; mesma placa permitida em oficinas diferentes.
2. Filtro automático Hibernate em JPQL e bloqueio de FK entre oficinas; ausência de contexto não retorna dados.
3. Login, refresh de uso único, revogação, usuário inativo, papéis e DTO de usuário sem senha.
4. Bootstrap da oficina idempotente, sem redefinir senha existente.
5. Fluxo completo da OS, valores por versão, rejeição de versão substituída e prova de aprovação.
6. Isolamento dos sub-recursos da OS e links inválidos, expirados ou revogados.
7. Imutabilidade no banco: update/delete bloqueados, inserção tardia de item rejeitada e total sem itens rejeitado.
8. Checklist, diagnóstico, upload/download de imagem e associação obrigatória à mesma OS/oficina.
9. Duas aprovações simultâneas geram uma única decisão persistida.
10. OpenAPI, validação de entrada e bloqueio de salto inválido de status.

## Limites da execução local

O Docker Desktop reportou “Virtual Machine Platform not enabled / No virtualization available”. Nenhuma configuração de virtualização do Windows foi alterada. O modo alternativo da suíte utilizou PostgreSQL real, mas substituiu o armazenamento S3 por uma implementação em memória. O CI usa MinIO real e precisa executar para confirmar esse caminho e o build da imagem.

O bootstrap automático do wrapper oficial falhou ao renomear o diretório temporário no Windows. Para verificar o wrapper, a distribuição oficial já baixada e conferida foi extraída diretamente no cache de `.tools/wrapper-home`. O script oficial foi mantido. Em ambientes normais ele baixa Maven automaticamente; nesta máquina, o Maven portátil em `.tools/apache-maven-3.9.11` também funciona.

## Pendências de aceite

- Aprovar o [fluxo da interface](proposta-interface.md) antes de implementar React + TypeScript + Vite, conforme o fluxo humano de aprovação solicitado.
- Executar o CI com PostgreSQL e MinIO via Testcontainers e o build Docker.
- Definir destino e política de staging antes de implementar/publicar deploy.
- Criar o fluxo de revisão no GitHub; esta entrega permaneceu local, sem push, PR ou merge.

As migrations têm scripts de reversão revisáveis, mas rollback em banco com dados é destrutivo. Os testes de reversão foram feitos exclusivamente em schema vazio e temporário.
