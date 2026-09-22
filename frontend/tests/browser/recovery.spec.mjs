import { empresa } from './company-fixture.mjs';
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

async function setup(page, role = 'OWNER') {
  const opportunity = { id: 'op1', tipo: 'ORCAMENTO_ESQUECIDO', status: 'ABERTA', revisao: 0,
    cliente: { id: 'c1', nome: 'Mariana', telefone: '11912345678', email: null },
    veiculo: { id: 'v1', placa: 'ABC1D23', marca: 'Fiat', modelo: 'Uno' },
    origem: { ordemServicoId: 'os1', numeroOs: 42, orcamentoVersaoId: 'version1' }, responsavel: null,
    valorPotencial: 2000, criadoEm: '2026-09-16T12:00:00Z', elegivelDesde: '2026-09-09T12:00:00Z', diasEmAberto: 7,
    ultimoContatoEm: null, proximoContatoEm: null, encerradaEm: null };
  const state = { opportunity, contacts: [], recovered: null, writes: [], queries: [], fail: false, conflict: false, empty: false };
  await page.route('**/api/v1/**', async route => {
    const req = route.request(), url = new URL(req.url()), path = url.pathname.replace('/api/v1', '');
    const send = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (path.endsWith('/empresa')) return send(empresa);
    // Sem cookie de sessão, a retomada do carregamento é recusada — como num navegador limpo.
    if (path === '/auth/refresh') return send({ detail: 'Sessão expirada.' }, 401);
    if (path === '/auth/login') return send({ accessToken: 'access', refreshToken: 'refresh', oficinaId: 'tenant', usuarioId: 'u', nome: 'Kauã', papel: role, expiresIn: 900 });
    if (path === '/dashboard') return send({ emAndamento: 0, prontas: 0, porStatus: {}, orcamentosAguardandoDecisao: { total: 0, quantidade: 0 } });
    if (!path.startsWith('/dinheiro-esquecido')) return send({ itens: [], pagina: 0, tamanho: 10, total: 0 });
    expect(role).not.toBe('MECANICO'); expect(req.headers().authorization).toBe('Bearer access');
    if (state.fail) return send({ detail: 'Serviço indisponível' }, 503);
    if (path.endsWith('/resumo')) return send({ oportunidadesAbertas: state.recovered ? 0 : 1, valorPotencialConhecido: state.recovered ? 0 : 2000, valorRecuperado: state.recovered?.valorRecuperado ?? 0, quantidadeRecuperada: state.recovered ? 1 : 0 });
    if (req.method() === 'POST') {
      const body = req.postData() ? req.postDataJSON() : null;
      state.writes.push({ path, body });
      if (path.endsWith('/identificar')) return send({ criadas: 1, descartadas: 0 });
      if (state.conflict) { state.conflict = false; opportunity.revisao++; return send({ code: 'CONFLICT', detail: 'Registro alterado.' }, 409); }
      expect(body.revisao).toBe(opportunity.revisao);
      opportunity.revisao++;
      if (path.endsWith('/contatos')) { state.contacts.push({ id: 'contact', ...body, realizadoEm: '2026-09-16T13:00:00Z' }); opportunity.status = 'EM_CONTATO'; }
      if (path.endsWith('/resultados')) { state.recovered = { valorRecuperado: Number(body.valorRecuperado), registradoEm: '2026-09-16T13:30:00Z', observacao: body.observacao }; opportunity.status = 'RECUPERADA'; }
      return send(opportunity, 201);
    }
    if (path.endsWith('/oportunidades')) {
      state.queries.push(url.searchParams.toString());
      const empty = state.empty || url.searchParams.get('status') === 'PERDIDA';
      return send({ itens: empty ? [] : [opportunity], pagina: Number(url.searchParams.get('pagina')), tamanho: 10, total: empty ? 0 : 11 });
    }
    return send({ oportunidade: opportunity, contatos: state.contacts, resultado: state.recovered, auditoria: [] });
  });
  await page.goto('/');
  await page.getByLabel('Empresa', { exact: true }).fill('oficina');
  await page.getByLabel('E-mail', { exact: true }).fill('kaua@example.test');
  await page.getByLabel('Senha', { exact: true }).fill('senha-teste');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Início', level: 1 })).toBeVisible();
  return state;
}
async function open(page) { await page.getByRole('button', { name: 'Ver retornos' }).click(); }
async function accessible(page) {
  const report = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(report.violations.map(v => ({ id: v.id, nodes: v.nodes.map(n => n.target) }))).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
}
test('carteira real, filtros, paginação, contato e recuperação com valor explícito', async ({ page }, info) => {
  const state = await setup(page); await open(page);
  await expect(page.getByText('total da versão publicada', { exact: false })).toBeVisible();
  await accessible(page);
  await page.getByRole('button', { name: 'Identificar oportunidades', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '1 oportunidades criadas' })).toBeVisible();
  await page.getByRole('button', { name: 'Próxima página', exact: true }).click();
  await expect.poll(() => state.queries.some(q => q.includes('pagina=1'))).toBe(true);
  await page.getByLabel('Status da oportunidade').selectOption('PERDIDA');
  await expect(page.getByRole('heading', { name: 'Nenhuma oportunidade encontrada' })).toBeVisible();
  await page.getByRole('button', { name: 'Limpar filtros' }).click();
  await page.getByRole('button', { name: 'Ver oportunidade de Mariana' }).click();
  await page.getByRole('button', { name: 'Registrar contato', exact: true }).click();
  await page.getByLabel('Canal', { exact: true }).selectOption('WHATSAPP');
  await page.getByLabel('Observação', { exact: true }).fill('Cliente quer retornar');
  await page.getByRole('button', { name: 'Salvar contato', exact: true }).click();
  await expect(page.getByText('WhatsApp · Contato realizado', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Marcar valor recuperado' }).click();
  await expect(page.getByLabel('Valor recuperado (R$)')).toHaveValue('');
  await page.getByLabel('Valor recuperado (R$)').fill('1450,00');
  await page.getByRole('button', { name: 'Revisar recuperação' }).click();
  expect(state.recovered).toBeNull();
  await page.getByRole('button', { name: 'Confirmar e salvar' }).click();
  await expect(page.getByText('Valor recuperado:', { exact: false })).toContainText('1.450,00');
  await expect(page.getByRole('button', { name: 'Marcar valor recuperado' })).toHaveCount(0);
  expect(state.writes.filter(w => w.path.endsWith('/resultados'))).toHaveLength(1);
  await accessible(page); await page.screenshot({ path: info.outputPath('recuperacao.png'), fullPage: true });
});
test('erro recuperável, valor desconhecido e conflito sem repetir escrita', async ({ page }) => {
  const state = await setup(page); state.fail = true; await open(page);
  await expect(page.getByRole('heading', { name: 'Não foi possível carregar oportunidades' })).toBeVisible();
  state.fail = false; state.opportunity.tipo = 'REVISAO_ATRASADA'; state.opportunity.valorPotencial = null;
  await page.getByRole('button', { name: 'Tentar novamente' }).click();
  await expect(page.getByText('Não avaliado', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Ver oportunidade de Mariana' }).click();
  await page.getByRole('button', { name: 'Registrar contato', exact: true }).click();
  state.conflict = true;
  await page.getByRole('button', { name: 'Salvar contato', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: 'A oportunidade mudou' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Registrar contato', exact: true })).toBeVisible();
  expect(state.contacts).toHaveLength(0); expect(state.writes).toHaveLength(1);
});
test('mecânico não recebe ações nem consulta oportunidades', async ({ page }) => {
  await setup(page, 'MECANICO');
  await expect(page.getByRole('button', { name: 'Ver retornos' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Retornos', exact: true })).toHaveCount(0);
});
