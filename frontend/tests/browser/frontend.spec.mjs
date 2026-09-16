import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

// Contract-controlled browser tests. No fixture or mock is imported by production.
const token = 'a'.repeat(43);
const session = { accessToken: 'test-access', refreshToken: 'test-refresh', oficinaId: 'oficina', usuarioId: 'owner', nome: 'Kauã', papel: 'OWNER', expiresIn: 900 };
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64');

async function fixture(context, role = 'OWNER') {
  const state = {
    clients: [], vehicles: [], orders: [], versions: [], diagnostics: [], photos: [], timeline: [], checklist: null,
    users: [{ id: 'mechanic', nome: 'Mecânico de teste', email: 'mechanic@example.test', papel: 'MECANICO', ativo: true }],
    failClients: false, fieldError: false, expired: false, delay: 0, writes: [], statusConflict: false,
  };
  const emit = description => state.timeline.push({ id: String(state.timeline.length), descricao: description, origem: 'USUARIO', criadoEm: new Date().toISOString() });
  await context.route('**/api/v1/**', async route => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname.replace('/api/v1', '');
    const method = req.method();
    const body = req.headers()['content-type']?.includes('application/json') ? req.postDataJSON() : {};
    const send = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });
    const page = rows => {
      const pagina = Number(url.searchParams.get('pagina') ?? 0), tamanho = Number(url.searchParams.get('tamanho') ?? 20);
      const busca = (url.searchParams.get('busca') ?? '').toLowerCase();
      const filtered = rows.filter(row => JSON.stringify(row).toLowerCase().includes(busca));
      return { itens: filtered.slice(pagina * tamanho, (pagina + 1) * tamanho), pagina, tamanho, total: filtered.length, totalPaginas: Math.ceil(filtered.length / tamanho) };
    };
    if (state.delay) await new Promise(resolve => setTimeout(resolve, state.delay));
    if (path.startsWith('/publico/')) {
      expect(req.headers().authorization).toBeUndefined();
      const order = state.orders[0];
      if (!order) return send({ detail: 'Link inválido.' }, 404);
      if (method === 'POST') {
        state.writes.push(path);
        state.versions.at(-1).decisao = { aprovado: body.aprovado, criadoEm: new Date().toISOString(), canal: 'LINK_PUBLICO' };
        order.status = body.aprovado ? 'EM_MANUTENCAO' : 'ORCAMENTO';
      }
      return send({ numero: order.numero, status: order.status, veiculo: 'Fiat Uno · ABC1D23', previsaoEntrega: null, orcamento: state.versions.at(-1) ?? null });
    }
    if (path === '/auth/login') return send({ ...session, papel: role });
    if (path === '/auth/logout') return route.fulfill({ status: 204 });
    if (state.expired) return send({ detail: 'Sessão expirada.' }, 401);
    if (path === '/auth/refresh') return send({ ...session, papel: role });
    expect(req.headers().authorization).toBe('Bearer test-access');
    if (path === '/dashboard') return state.failClients ? send({ detail: 'Serviço indisponível.' }, 503) : send({ emAndamento: state.orders.filter(o => o.status !== 'PRONTO').length, prontas: state.orders.filter(o => o.status === 'PRONTO').length, porStatus: { AGUARDANDO_APROVACAO: state.orders.filter(o => o.status === 'AGUARDANDO_APROVACAO').length }, orcamentosAguardandoDecisao: { total: 0, quantidade: 0 } });
    if (path === '/usuarios') return send(page(state.users));
    for (const [resource, key] of [['clientes', 'clients'], ['veiculos', 'vehicles']]) {
      if (!path.startsWith(`/${resource}`)) continue;
      if (resource === 'clientes' && state.failClients) return send({ detail: 'Serviço indisponível.' }, 503);
      if (method === 'GET') return send(path === `/${resource}` ? page(state[key]) : state[key].find(v => path.endsWith(v.id)));
      if (state.fieldError && resource === 'clientes') return send({ detail: 'Confira os campos.', code: 'VALIDATION_ERROR', errors: [{ field: 'nome', message: 'Informe o nome completo.' }] }, 400);
      const id = method === 'PUT' ? path.split('/').at(-1) : `${resource}-${state[key].length + 1}`;
      const saved = { ...body, id, revisao: (body.revisao ?? 0) + 1 };
      state[key] = [...state[key].filter(v => v.id !== id), saved];
      state.writes.push(path); return send(saved, method === 'POST' ? 201 : 200);
    }
    if (path === '/ordens-servico') {
      if (method === 'GET') return send(page(state.orders));
      const vehicle = state.vehicles.find(v => v.id === body.veiculoId);
      const order = { ...body, id: 'os-1', numero: 1, clienteId: vehicle.clienteId, status: 'RECEBIDO', revisao: 0, criadoEm: new Date().toISOString(), concluidaEm: null };
      state.orders.push(order); state.writes.push(path); emit('OS aberta'); return send(order, 201);
    }
    const order = state.orders[0];
    const suffix = path.replace('/ordens-servico/os-1', '');
    if (method === 'GET') {
      if (suffix === '') return send(order);
      if (suffix === '/checklist') return state.checklist ? send(state.checklist) : send({ detail: 'Checklist não registrado.' }, 404);
      if (suffix === '/diagnosticos') return send(state.diagnostics);
      if (suffix === '/orcamento/versoes') return send(state.versions);
      if (suffix === '/timeline') return send(state.timeline);
      if (suffix === '/fotos') return send(state.photos);
      if (suffix.endsWith('/conteudo')) return route.fulfill({ contentType: 'image/png', body: png });
    }
    state.writes.push(path);
    if (suffix === '/status') {
      if (state.statusConflict) { state.statusConflict = false; order.revisao++; return send({ code: 'CONFLICT', detail: 'A OS mudou. Atualize os dados.' }, 409); }
      expect(body.revisao).toBe(order.revisao);
      order.status = body.status; order.revisao++; emit('Status atualizado'); return send(order);
    }
    if (suffix === '/responsavel') { order.mecanicoId = body.mecanicoId; order.revisao++; return send(order); }
    if (suffix === '/checklist') { state.checklist = { ...body, id: 'checklist', itens: body.itens.map((i, n) => ({ ...i, id: `item-${n}` })) }; emit('Checklist registrado'); return send(state.checklist, 201); }
    if (suffix === '/diagnosticos') {
      expect(['VERDE', 'AMARELO', 'VERMELHO']).toContain(body.classificacao);
      const d = { ...body, id: 'diagnostic', criadoEm: new Date().toISOString() }; state.diagnostics.push(d); emit('Diagnóstico registrado'); return send(d, 201);
    }
    if (suffix === '/orcamento/versoes') {
      const v = { ...body, id: `version-${state.versions.length + 1}`, numero: state.versions.length + 1, criadoEm: new Date().toISOString(), decisao: null,
        itens: body.itens.map((i, n) => ({ ...i, id: `budget-${n}`, subtotal: i.quantidade * i.valorUnitario })), total: body.itens.reduce((sum, i) => sum + i.quantidade * i.valorUnitario, 0) };
      state.versions.push(v); order.status = 'ORCAMENTO'; emit('Orçamento criado'); return send(v, 201);
    }
    if (suffix === '/fotos') { const p = { id: 'photo', descricao: 'Entrada', finalidade: 'ENTRADA', checklistItemId: null, diagnosticoItemId: null }; state.photos.push(p); emit('Foto adicionada'); return send(p, 201); }
    if (suffix === '/links') return send({ id: 'link', token, url: `http://api.invalid/acompanhar#${token}`, expiraEm: '2099-01-01T00:00:00Z' }, 201);
    if (suffix === '/links/link') return route.fulfill({ status: 204 });
    throw Error(`Unexpected API request: ${method} ${path}`);
  });
  return state;
}
async function login(page) {
  await page.goto('/');
  await page.getByLabel('Oficina', { exact: true }).fill('oficina');
  await page.getByLabel('E-mail', { exact: true }).fill('kaua@example.test');
  await page.getByLabel('Senha', { exact: true }).fill('test-password');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
}
async function nav(page, name) {
  const menu = page.getByRole('button', { name: 'Abrir menu', exact: true });
  if (await menu.isVisible()) await menu.click();
  await page.getByRole('navigation', { name: 'Navegação principal', exact: true }).filter({ visible: true }).getByRole('button', { name, exact: true }).click();
}
async function noOverflow(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
}
async function accessible(page) {
  const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(result.violations.map(v => ({ id: v.id, nodes: v.nodes.map(n => n.target) }))).toEqual([]);
}
async function saved(page) { await expect(page.locator('dialog[open]')).toHaveCount(0); }
async function status(page, value) {
  await page.getByRole('button', { name: 'Atualizar status', exact: true }).click();
  await page.getByLabel('Próxima etapa *').selectOption(value);
  await page.getByRole('button', { name: 'Confirmar status', exact: true }).click();
  await saved(page);
}

test('atualização automática preserva rascunho e recupera falha sem apagar a OS', async ({ page, context }) => {
  await page.clock.install();
  const state = await fixture(context);
  state.clients = [{ id: 'c', nome: 'Cliente', telefone: '11912345678', email: '', revisao: 0 }];
  state.vehicles = [{ id: 'v', clienteId: 'c', placa: 'ABC1D23', marca: 'Fiat', modelo: 'Uno', ano: 2020, km: 100, cor: 'Prata', revisao: 0 }];
  state.orders = [{ id: 'os-1', numero: 1, clienteId: 'c', veiculoId: 'v', status: 'DIAGNOSTICO', kmEntrada: 100, relato: 'Ruído', revisao: 0, criadoEm: new Date().toISOString() }];
  await login(page); await nav(page, 'Ordens de Serviço');
  await page.getByRole('button', { name: 'Ver detalhes da OS 1' }).click();
  await page.getByRole('tab', { name: 'Diagnóstico', exact: true }).click();
  await page.getByRole('button', { name: 'Adicionar item', exact: true }).click();
  await page.getByLabel('Avaliação técnica *').fill('Rascunho preservado');
  state.orders[0].status = 'ORCAMENTO';
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(page.getByLabel('Avaliação técnica *')).toHaveValue('Rascunho preservado');
  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
  await expect(page.getByText('Esta OS é atualizada automaticamente', { exact: false })).toBeVisible();
  await page.route('**/api/v1/ordens-servico/os-1', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ detail: 'Indisponível' }) }));
  await page.clock.fastForward(16_000);
  await expect(page.getByRole('status').filter({ hasText: 'Não foi possível atualizar automaticamente' })).toBeVisible();
  await expect(page.getByRole('heading', { name: /OS #1/ })).toBeVisible();
  await page.unroute('**/api/v1/ordens-servico/os-1');
  await page.clock.fastForward(16_000);
  await expect(page.getByRole('heading', { name: /OS #1/ })).toContainText('Orçamento');
  await expect(page.getByText('Não foi possível atualizar automaticamente.', { exact: false })).toHaveCount(0);
  await noOverflow(page);
});

test('fluxo completo: cliente, veículo, OS, checklist, diagnóstico, orçamento, foto e aprovação pública', async ({ page, context }, info) => {
  const state = await fixture(context);
  const errors = []; page.on('pageerror', e => errors.push(e.message));
  await login(page); await expect(page.getByRole('heading', { name: 'Um dia bem organizado.' })).toBeVisible();
  await noOverflow(page); await accessible(page);
  await nav(page, 'Clientes');
  await page.getByRole('button', { name: 'Cadastrar cliente', exact: true }).click();
  await page.getByLabel('Nome completo *').fill('Cliente de teste');
  await page.getByLabel('Telefone *').fill('11912345678');
  await accessible(page);
  await page.getByRole('button', { name: 'Salvar', exact: true }).click(); await saved(page);
  await page.getByRole('button', { name: 'Cliente de teste', exact: true }).click();
  await page.getByRole('button', { name: 'Editar cadastro', exact: true }).click();
  await page.getByLabel('Nome completo *').fill('Cliente atualizado');
  await page.getByRole('button', { name: 'Salvar', exact: true }).click(); await saved(page);
  expect(state.clients[0].nome).toBe('Cliente atualizado');
  await nav(page, 'Veículos'); await page.getByRole('button', { name: 'Cadastrar veículo', exact: true }).click();
  await page.getByLabel('Cliente *', { exact: true }).selectOption(state.clients[0].id);
  await page.getByLabel('Placa *').fill('ABC1D23'); await page.getByLabel('Marca *').fill('Fiat');
  await page.getByLabel('Modelo *').fill('Uno'); await page.getByLabel('Ano *').fill('2020'); await page.getByLabel('Cor *').fill('Prata');
  await page.getByRole('button', { name: 'Salvar', exact: true }).click(); await saved(page);
  await page.getByRole('button', { name: 'ABC1D23', exact: true }).click();
  await page.getByRole('button', { name: 'Editar cadastro', exact: true }).click();
  await page.getByLabel('Cor *').fill('Preto'); await page.getByRole('button', { name: 'Salvar', exact: true }).click(); await saved(page);
  expect(state.vehicles[0].cor).toBe('Preto'); await noOverflow(page);
  await nav(page, 'Ordens de Serviço'); await page.getByRole('button', { name: 'Abrir OS', exact: true }).click();
  await page.getByLabel('Veículo *', { exact: true }).selectOption(state.vehicles[0].id);
  await page.getByLabel('Relato do cliente *').fill('Avaliar ruído do motor.');
  await page.getByRole('dialog').getByRole('button', { name: 'Abrir OS', exact: true }).click(); await saved(page);
  await expect(page.getByRole('heading', { name: /OS #1/ })).toBeVisible();
  await page.getByRole('tab', { name: 'Checklist', exact: true }).click();
  await page.getByRole('button', { name: 'Registrar checklist', exact: true }).click();
  await page.getByLabel('Descrição *', { exact: true }).fill('Carroceria'); await page.getByLabel('Condição *').fill('Bom estado');
  await page.getByRole('dialog').getByRole('button', { name: 'Registrar checklist', exact: true }).click();
  expect(state.checklist).toBeNull();
  await page.getByRole('button', { name: 'Confirmar e salvar', exact: true }).click(); await saved(page);
  await page.getByRole('tab', { name: 'Diagnóstico', exact: true }).click();
  await page.getByRole('button', { name: 'Adicionar item', exact: true }).click();
  await page.getByLabel('Avaliação técnica *').fill('Trocar filtro'); await page.getByLabel('Classificação *').selectOption('TROCAR');
  await page.getByRole('button', { name: 'Registrar item', exact: true }).click(); await saved(page);
  expect(state.diagnostics[0].classificacao).toBe('VERMELHO');
  await status(page, 'DIAGNOSTICO'); await status(page, 'ORCAMENTO');
  await page.getByRole('tab', { name: 'Orçamento', exact: true }).click();
  await page.getByRole('button', { name: 'Criar orçamento', exact: true }).click();
  await page.getByLabel('Descrição *', { exact: true }).fill('Filtro'); await page.getByLabel('Valor unitário (R$) *').fill('50');
  await page.getByRole('button', { name: 'Criar versão 1', exact: true }).click();
  await page.getByRole('button', { name: 'Confirmar e salvar', exact: true }).click(); await saved(page);
  expect(state.versions).toHaveLength(1);
  await page.getByRole('button', { name: 'Disponibilizar orçamento', exact: true }).click();
  expect(state.orders[0].status).toBe('ORCAMENTO');
  await page.getByRole('button', { name: 'Confirmar disponibilização', exact: true }).click(); await saved(page);
  await page.getByRole('button', { name: 'Gerar link do cliente', exact: true }).click();
  await expect(page.getByLabel('Link do cliente', { exact: true })).toHaveValue(`http://127.0.0.1:5179/acompanhar#${token}`);
  await page.getByRole('tab', { name: 'Fotos', exact: true }).click();
  await page.getByRole('button', { name: 'Adicionar foto', exact: true }).click();
  await page.getByLabel('Foto *', { exact: true }).setInputFiles({ name: 'entrada.png', mimeType: 'image/png', buffer: png });
  await page.getByRole('dialog').getByRole('button', { name: 'Adicionar foto', exact: true }).click(); await saved(page);
  await expect(page.getByRole('img', { name: 'Entrada', exact: true })).toBeVisible();
  await page.getByRole('tab', { name: 'Timeline', exact: true }).click();
  await expect(page.getByRole('list', { name: 'Histórico da ordem de serviço' })).toContainText('Foto adicionada');
  await noOverflow(page); await accessible(page);
  await page.screenshot({ path: info.outputPath('ordem-historico.png'), fullPage: true });
  const publicPage = await context.newPage();
  await publicPage.goto(`/acompanhar#${token}`);
  await publicPage.getByRole('button', { name: /Aprovar R/ }).click();
  expect(state.orders[0].status).toBe('AGUARDANDO_APROVACAO');
  await publicPage.getByRole('button', { name: 'Confirmar aprovação', exact: true }).click();
  await expect(publicPage.getByRole('status')).toContainText('Aprovação registrada');
  await noOverflow(publicPage); await accessible(publicPage);
  // The workshop receives the public decision without a manual reload.
  await page.bringToFront();
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(page.getByRole('heading', { name: /OS #1/ })).toContainText('Em manutenção');
  await status(page, 'TESTE'); await status(page, 'PRONTO');
  await expect(page.getByRole('button', { name: 'Atualizar status', exact: true })).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('carregamento, erro recuperável, validação por campo, busca vazia e sessão expirada', async ({ page, context }) => {
  const state = await fixture(context); state.failClients = true;
  await login(page);
  await expect(page.getByRole('heading', { name: 'Não foi possível carregar os dados' })).toBeVisible();
  state.failClients = false; state.delay = 200;
  await page.getByRole('button', { name: 'Tentar novamente', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Carregando dados da oficina' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Um dia bem organizado.' })).toBeVisible(); state.delay = 0;
  await nav(page, 'Clientes'); await page.getByRole('button', { name: 'Cadastrar cliente', exact: true }).click();
  await page.getByLabel('Nome completo *').fill('A'); await page.getByLabel('Telefone *').fill('11912345678');
  state.fieldError = true;
  await page.getByRole('button', { name: 'Salvar', exact: true }).click();
  await expect(page.getByLabel('Nome completo *')).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByLabel('Nome completo *')).toBeFocused();
  await accessible(page);
  state.fieldError = false;
  await page.getByLabel('Nome completo *').fill('Nome completo');
  await page.getByRole('button', { name: 'Salvar', exact: true }).click(); await saved(page);
  await page.getByRole('textbox', { name: 'Buscar por nome, telefone ou e-mail' }).fill('inexistente');
  await expect(page.getByRole('heading', { name: 'Nenhum resultado encontrado' })).toBeVisible();
  state.expired = true;
  await page.getByRole('button', { name: 'Atualizar dados', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('Sessão expirada');
});

test('login acessível, proteção após reload e link público inválido', async ({ page, context }) => {
  await fixture(context);
  await page.goto('/'); await accessible(page); await noOverflow(page);
  await login(page); await expect(page.getByRole('heading', { name: 'Um dia bem organizado.' })).toBeVisible();
  await page.reload(); await expect(page.getByRole('button', { name: 'Entrar', exact: true })).toBeVisible();
  await page.goto('/acompanhar#invalido');
  await expect(page.getByRole('heading', { name: 'Link indisponível' })).toBeVisible();
  await accessible(page); await noOverflow(page);
});

test('mecânico consulta cadastros sem ações de escritório', async ({ page, context }) => {
  await fixture(context, 'MECANICO'); await login(page);
  await expect(page.getByRole('heading', { name: 'Um dia bem organizado.' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Abrir OS', exact: true })).toHaveCount(0);
  await nav(page, 'Clientes'); await expect(page.getByRole('button', { name: 'Cadastrar cliente', exact: true })).toHaveCount(0);
  await nav(page, 'Veículos'); await expect(page.getByRole('button', { name: 'Cadastrar veículo', exact: true })).toHaveCount(0);
});

test('paginação remota, busca por e-mail e edição preservam registros fora da página', async ({ page, context }) => {
  const state = await fixture(context);
  state.clients = Array.from({ length: 21 }, (_, i) => ({ id: `c${i}`, nome: `Cliente ${String(i).padStart(2, '0')}`, telefone: '11912345678', email: `pessoa${i}@example.test`, revisao: 0 }));
  const reads = [];
  page.on('request', req => { if (req.url().includes('/clientes?')) reads.push(new URL(req.url())); });
  await login(page); await nav(page, 'Clientes');
  await expect(page.getByRole('button', { name: 'Cliente 00', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cliente 20', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Próxima página' }).click();
  await expect(page.getByRole('button', { name: 'Cliente 10', exact: true })).toBeVisible();
  const search = page.getByRole('textbox', { name: 'Buscar por nome, telefone ou e-mail' });
  await search.fill('pessoa20@example.test');
  await expect(page.getByRole('button', { name: 'Cliente 20', exact: true })).toBeVisible();
  await expect(search).toBeFocused();
  expect(reads.every(url => url.searchParams.get('tamanho') === '10')).toBe(true);
  expect(reads.some(url => url.searchParams.get('pagina') === '1')).toBe(true);
  await page.getByRole('button', { name: 'Cliente 20', exact: true }).click();
  await page.getByRole('button', { name: 'Editar cadastro' }).click();
  await page.getByLabel('Nome completo *').fill('Cliente editado');
  await page.getByRole('button', { name: 'Salvar', exact: true }).click(); await saved(page);
  await expect(page.getByRole('button', { name: 'Cliente editado', exact: true })).toBeVisible();
  expect(state.clients.find(c => c.id === 'c20').revisao).toBe(1);
  expect(state.clients).toHaveLength(21);
});

test('busca global consulta API e conflito de status permite revisão antes de tentar novamente', async ({ page, context }) => {
  const state = await fixture(context);
  state.clients = [{ id: 'c', nome: 'Cliente distante', telefone: '11912345678', email: '', revisao: 0 }];
  state.vehicles = [{ id: 'v', clienteId: 'c', placa: 'ABC1D23', marca: 'Fiat', modelo: 'Uno', ano: 2020, km: 100, cor: 'Prata', revisao: 0 }];
  state.orders = [{ id: 'os-1', numero: 1, clienteId: 'c', veiculoId: 'v', mecanicoId: 'mechanic', status: 'RECEBIDO', kmEntrada: 100, relato: 'Ruído', revisao: 0, criadoEm: new Date().toISOString() }];
  await login(page); await nav(page, 'Clientes');
  await page.getByRole('button', { name: 'Buscar na oficina', exact: true }).click();
  await page.getByRole('textbox', { name: 'OS, placa, veículo ou cliente' }).fill('ABC1D23');
  await expect(page.getByRole('button', { name: /Fiat Uno/ })).toBeVisible();
  await page.getByRole('button', { name: /Fiat Uno/ }).click();
  await expect(page.getByRole('heading', { name: 'Cadastro do veículo' })).toBeVisible();
  await page.getByRole('button', { name: 'Fechar painel', exact: true }).click();
  await nav(page, 'Ordens de Serviço');
  await page.getByRole('button', { name: 'Ver detalhes da OS 1' }).click();
  state.statusConflict = true;
  await page.getByRole('button', { name: 'Atualizar status', exact: true }).click();
  await page.getByLabel('Próxima etapa *').selectOption('DIAGNOSTICO');
  await page.getByRole('button', { name: 'Confirmar status', exact: true }).click();
  await expect(page.locator('dialog[open]')).toContainText('A OS mudou');
  expect(state.orders[0].status).toBe('RECEBIDO');
  await page.getByRole('button', { name: 'Confirmar status', exact: true }).click(); await saved(page);
  expect(state.orders[0].status).toBe('DIAGNOSTICO');
  expect(state.orders[0].revisao).toBe(2);
});
