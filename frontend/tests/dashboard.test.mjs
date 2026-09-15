import test from "node:test";
import assert from "node:assert/strict";
import { getDashboard, isSameDay } from "../src/dashboard-model.ts";

const today = new Date(2026, 8, 15, 12);
const at = (day, hour = 10) => new Date(2026, 8, day, hour).toISOString();
const order = (patch = {}) => ({
  id: "os1", numero: 1001, status: "AGUARDANDO_APROVACAO", clienteId: "c1", veiculoId: "v1", mecanicoId: "u1",
  criadoEm: at(15), previsaoEntrega: at(16), kmEntrada: 100, revisao: 0, relato: "Revisão",
  versoes: [], diagnosticos: [], fotos: [], timeline: [], ...patch,
});
const version = (numero, total, decisao) => ({ id: `v${numero}`, numero, total, observacoes: "", criadoEm: at(15), itens: [], decisao });

test("soma somente a versão mais recente e não duplica o histórico", () => {
  const result = getDashboard([order({ versoes: [version(2, 804.8), version(1, 425)] })], today);
  assert.equal(result.pendingTotal, 804.8);
  assert.equal(result.pendingBudgets.length, 1);
});
test("exclui decisão registrada e OS fora de aguardando aprovação", () => {
  const result = getDashboard([
    order({ versoes: [version(1, 100), version(2, 200, { aprovado: false })] }),
    order({ id: "os2", status: "EM_MANUTENCAO", versoes: [version(1, 500)] }),
  ], today);
  assert.equal(result.pendingTotal, 0);
  assert.deepEqual(result.pendingBudgets, []);
});
test("OS pronta não gera pendência de atraso e não conta em andamento", () => {
  const result = getDashboard([order({ status: "PRONTO", previsaoEntrega: at(14) })], today);
  assert.equal(result.ongoing.length, 0);
  assert.equal(result.ready.length, 1);
  assert.equal(result.priorities.length, 0);
});
test("prioriza atrasos e preserva os dados recebidos", () => {
  const input = [order(), order({ id: "atrasada", status: "DIAGNOSTICO", previsaoEntrega: at(14) })];
  const before = structuredClone(input);
  assert.equal(getDashboard(input, today).priorities[0].order.id, "atrasada");
  assert.deepEqual(input, before);
});
test("entradas usam o dia local e a data real de recebimento", () => {
  assert.equal(isSameDay(at(15, 0), today), true);
  assert.equal(isSameDay(at(14, 23), today), false);
  const result = getDashboard([order(), order({ id: "ontem", criadoEm: at(14) })], today);
  assert.equal(result.entries.length, 1);
});
test("base vazia tem estados sem valores inventados", () => {
  const result = getDashboard([], today);
  assert.equal(result.pendingTotal, 0);
  assert.deepEqual(result.priorities, []);
  assert.deepEqual(result.entries, []);
});
