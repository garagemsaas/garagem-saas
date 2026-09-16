# Fase 4 — frente de frontend do Kauã

Base sincronizada em `f25878b`, depois da integração da entrega do Cauã pelo PR #11.
Branch compartilhada: `feature/fase4-os-public-tracking`.

## Entrega

- Detalhe da OS consulta os recursos existentes a cada 15 segundos enquanto a aba
  está visível; retornar à janela também atualiza status, orçamento e timeline.
  A aprovação/recusa pública aparece sem exigir recarga manual da oficina.
- Consultas automáticas não se sobrepõem, pausam durante formulários e gravações,
  descartam respostas após sair do detalhe e preservam o link emitido na sessão.
- Falha de atualização mantém os últimos dados e mostra aviso recuperável. Uma
  leitura bem-sucedida remove o aviso. A proteção de expiração da sessão existente
  continua responsável por remover os dados da oficina após um 401.
- Botão de copiar link, com alternativa manual se a área de transferência falhar.
- Link expirado deixa de oferecer compartilhamento; a interface permite emitir outro.
- Orientação explícita de que o endereço precisa ser copiado na sessão atual e que
  gerar outro link não revoga os anteriores.

Checklist, diagnóstico, fotos autenticadas, versões imutáveis, responsável, status,
permissões e página pública já estavam implementados. Foram preservados e incluídos
na regressão, sem reintroduzir protótipos ou dados fictícios no produto.

## Validação e aceite técnico

Resultado local: lint, typecheck, build, 23 testes unitários e 30 cenários de
navegador aprovados. A execução final do navegador passou integralmente, com um
worker, em 59 segundos. `git diff --check` sem erros.

Comandos na pasta `frontend`:

```powershell
npm.cmd run lint
npm.cmd run typecheck
npm.cmd test
npm.cmd run build
npm.cmd run test:ui -- --workers=1
```

A suíte de navegador usa os contratos REST controlados apenas nos testes, nos
tamanhos 1440×1000, 768×1024 e 390×844. Inclui fluxo de cadastro até conclusão da OS,
checklist, diagnóstico, foto privada, orçamento, aprovação pública, erros, sessão,
papéis, revisão concorrente, acessibilidade e ausência de overflow da página.

Novas regressões cobrem atualização periódica, pausa em rascunhos, recuperação de
falha, decisão pública refletida na oficina sem botão de recarga, orçamento
substituído exigindo nova confirmação e comprovante de recusa após reload.

O contraste do texto auxiliar foi corrigido durante a validação com axe. O teste de
cancelamento aceita explicitamente a confirmação de descarte do rascunho.

## Limites e continuação

1. **Links anteriores após reload:** a API não lista metadata dos links emitidos.
   Sua gestão permanece pendente de contrato com Cauã. Proposta para avaliação:
   listagem autenticada, restrita à oficina e papéis de escritório, contendo ID,
   emissão, expiração e revogação, sem token ou hash; usar o DELETE já existente.
   Esta entrega não cria endpoint, não guarda tokens em armazenamento persistente
   e não promete revogar links que a sessão não conhece.
2. **Ambiente real:** Docker não estava acessível nesta máquina nesta execução
   (`dockerDesktopLinuxEngine` ausente). Os testes de PostgreSQL/MinIO do Cauã estão
   documentados em `fase4-caua.md`; não equivalem a uma nova execução desta entrega.
   Repetir o aceite integrado antes de liberar para oficinas piloto.
3. **Aceite de produto:** revisão de Kauã/Cauã e teste com usuários continuam
   necessários. Este PR não declara staging publicado nem encerramento comercial.
4. Atualização automática cobre o detalhe aberto. Dashboard e listagens mantêm
   suas rotinas atuais de consulta; a frequência gera seis GETs por ciclo por OS aberta.

Roteiro integrado: abrir OS → checklist → diagnóstico → foto → orçamento v1/v2 →
disponibilizar → copiar link → decidir em outra aba → retornar à oficina → verificar
status, comprovante e timeline → avançar para teste/pronto. Repetir com recusa,
revogação, versão substituída, mecânico e outra oficina. Não aprovar o merge como
prova de que uma validação em ambiente real foi executada.
