import { empresa } from './company-fixture.mjs';
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
async function setup(page, role = 'OWNER') {
  const item = { id: 'r1', clienteId: 'c1', cliente: 'Mariana', telefone: '11912345678', veiculoId: null, veiculo: '', responsavelId: 'u', responsavel: 'Ana', motivo: 'Confirmar atendimento', agendadoEm: '2026-09-01T12:00:00Z', prioridade: 'ALTA', status: 'PENDENTE', revisao: 0 };
  const state = { itens: [item], writes: [], conflict: false };
  await page.route('**/api/v1/**', route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname.replace('/api/v1', '');
    const send = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    const pageOf = itens => ({ itens, pagina: 0, tamanho: 10, total: itens.length });
    if (path === '/empresa') return send(empresa);
    if (path === '/auth/refresh') return send({ detail: 'Sessão expirada.' }, 401);
    if (path === '/auth/login') return send({ accessToken: 'a', oficinaId: 'tenant', usuarioId: 'u', nome: 'Ana', papel: role, expiresIn: 900 });
    if (path === '/dashboard') return send({ emAndamento: 0, prontas: 0, porStatus: {}, orcamentosAguardandoDecisao: { total: 0, quantidade: 0 } });
    if (path === '/clientes') return send(pageOf([{ id: 'c1', nome: 'Mariana' }]));
    if (path === '/usuarios') return send(pageOf([{ id: 'u', nome: 'Ana', papel: 'OWNER', ativo: true }]));
    if (!path.startsWith('/retornos')) return send(pageOf([]));
    expect(role).not.toBe('MECANICO');
    if (req.method() !== 'GET') {
      const body = req.postData() ? req.postDataJSON() : {};state.writes.push({ path, body });
      if (state.conflict) return send({ detail: 'O retorno mudou. Atualize a lista.' }, 409);
      if (path.endsWith('/sugestoes')) return send({ criados: 0 });
      if (path === '/retornos') state.itens.push({ ...item, ...body, id: 'r2' });
      else Object.assign(item, body, { revisao: item.revisao + 1 });
      return send(item, req.method() === 'POST' ? 201 : 200);
    }
    const status = url.searchParams.get('status');return send(pageOf(state.itens.filter(r => status === 'TODOS' || r.status === status)));
  });
  await page.goto('/');await page.getByLabel('Empresa', { exact: true }).fill('empresa');await page.getByLabel('E-mail', { exact: true }).fill('ana@example.test');await page.getByLabel('Senha', { exact: true }).fill('senha');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();await expect(page.getByRole('heading', { name: 'Início', level: 1 })).toBeVisible();return state;
}
test('agenda permite reagendar, concluir e filtrar sem perder contexto', async ({ page }, testInfo) => {
  const state = await setup(page);await page.getByRole('button', { name: 'Ver retornos' }).click();
  await expect(page.getByRole('heading', { name: 'Mariana' })).toBeVisible();await expect(page.getByText('Responsável: Ana')).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('retornos.png'), fullPage: true });
  expect((await new AxeBuilder({ page }).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze()).violations).toEqual([]);
  await page.getByRole('button', { name: 'Editar ou reagendar', exact: true }).click();await page.getByLabel('Data e horário').fill('2026-10-01T14:30');await page.getByRole('button', { name: 'Salvar', exact: true }).click();
  await expect.poll(() => state.writes.length).toBe(1);await expect(page.getByRole('dialog', { name: 'Editar ou reagendar retorno' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Concluir', exact: true }).click();await page.getByLabel('Resultado do contato').fill('Cliente confirmou.');await page.getByRole('button', { name: 'Salvar', exact: true }).click();await page.getByRole('button', { name: 'Confirmar e salvar' }).click();
  await expect(page.getByRole('heading', { name: 'Nenhum retorno nesta seleção.' })).toBeVisible();await page.getByLabel('Situação', { exact: true }).selectOption('CONCLUIDO');await expect(page.getByText('Resultado: Cliente confirmou.')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
});
test('novo retorno e conflito sem repetir escrita', async ({ page }) => {
  const state = await setup(page);await page.getByRole('button', { name: 'Ver retornos' }).click();await page.getByRole('button', { name: 'Agendar retorno', exact: true }).click();
  await page.getByRole('combobox', { name: 'Selecionar cliente', exact: true }).selectOption('c1');await page.getByLabel('Motivo do contato').fill('Combinar retirada');await page.getByRole('button', { name: 'Salvar', exact: true }).click();
  await expect.poll(() => state.itens.length).toBe(2);await expect(page.getByRole('dialog', { name: 'Agendar retorno' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Cancelar retorno', exact: true }).first().click();state.conflict = true;await page.getByLabel('Motivo do cancelamento').fill('Solicitado pelo cliente');await page.getByRole('button', { name: 'Salvar', exact: true }).click();await page.getByRole('button', { name: 'Confirmar e salvar' }).click();await expect(page.getByRole('alert')).toContainText('O retorno mudou');expect(state.writes).toHaveLength(2);
});
test('mecânico não recebe agenda comercial', async ({ page }) => { await setup(page, 'MECANICO');await expect(page.getByRole('button', { name: 'Ver retornos' })).toHaveCount(0); });
