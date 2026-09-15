# Garagem SaaS

SaaS para oficinas mecânicas.

O Garagem SaaS organiza a operação da oficina desde o cadastro do cliente e do veículo até o diagnóstico, orçamento, aprovação, execução e conclusão da ordem de serviço.

O projeto utiliza arquitetura de monólito modular, API REST, frontend React e isolamento multi-tenant por oficina.

---

## Estado atual

### ✅ Fase 1 — Concluída

A Fase 1 do Garagem SaaS foi concluída e validada.

O núcleo operacional está funcional com frontend integrado à API real, PostgreSQL e armazenamento privado de fotos no MinIO.

O fluxo principal foi validado com backend, banco de dados e frontend funcionando em conjunto.

Fluxo validado:

```text
Login
  ↓
Cliente
  ↓
Veículo
  ↓
Ordem de Serviço
  ↓
Checklist
  ↓
Diagnóstico
  ↓
Fotos
  ↓
Orçamento
  ↓
Aprovação
  ↓
Execução / Status
  ↓
Timeline
  ↓
Conclusão
