// Run separately from npm test: requires a running API/PostgreSQL/MinIO and two
// disposable, pre-provisioned workshops. No network mocks or database mutations.
import fs from 'node:fs';
import assert from 'node:assert/strict';
import { pathToFileURL } from 'node:url';

const fixture = JSON.parse(fs.readFileSync(process.env.FASE1_FIXTURE, 'utf8'));
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ? pathToFileURL(process.env.PLAYWRIGHT_MODULE).href : 'playwright');
const base = process.env.FRONTEND_URL || 'http://127.0.0.1:5173';
const backend = process.env.API_URL || 'http://127.0.0.1:8080';
const output = process.env.E2E_OUTPUT || '../.tools/fase1-e2e';
fs.mkdirSync(output, { recursive: true });
const secrets = [fixture.password];
const browser = await chromium.launch({ headless: true });
let result = { steps: [], http: {}, errors: [], persisted: {} };
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  page.on('pageerror', e => result.errors.push(e.message));
  page.on('response', response => {
    const url = new URL(response.url());
    if (url.pathname.startsWith('/api/v1/')) result.http[response.status()] = (result.http[response.status()] || 0) + 1;
  });
  const step = text => { result.steps.push(text); console.log(text); };
  const button = name => page.getByRole('button', { name, exact: true });
  const visible = async locator => { await locator.waitFor({ state: 'visible', timeout: 15000 }); };
  const saved = async () => { await page.locator('dialog[open]').waitFor({ state: 'hidden', timeout: 15000 }); };
  const nav = name => page.getByRole('navigation', { name: 'Navegação principal', exact: true }).getByRole('button', { name, exact: true }).click();
  async function login(workshop) {
    await page.goto(base);
    await page.getByLabel('Oficina', { exact: true }).fill(workshop.slug);
    await page.getByLabel('E-mail', { exact: true }).fill(fixture.email);
    await page.locator('input[name="senha"]').fill(fixture.password);
    await button('Entrar').click();
    await visible(page.getByRole('heading', { name: 'Um dia bem organizado.' }));
  }
  await login(fixture.a); step('Login real da oficina A');
  await page.getByRole('navigation', { name: 'Administração', exact: true }).getByRole('button', { name: 'Equipe', exact: true }).click();
  await button('Cadastrar usuário').click();
  await page.getByLabel('Nome *', { exact: true }).fill('Mecânico Fase1');
  await page.getByLabel('E-mail *', { exact: true }).fill('mecanico-fase1@example.test');
  await page.getByLabel('Senha inicial *', { exact: true }).fill(fixture.password);
  await button('Salvar').click(); await saved(); step('Equipe cadastrada por OWNER na API');
  await nav('Clientes'); await button('Cadastrar cliente').click();
  await page.getByLabel('Nome completo *', { exact: true }).fill('Cliente Fase1 navegador');
  await page.getByLabel('Telefone *', { exact: true }).fill('11000000000');
  await page.getByLabel('E-mail', { exact: true }).fill('cliente-navegador@example.test');
  await button('Salvar').click(); await saved(); step('Cliente persistido pela interface');
  await nav('Veículos'); await button('Cadastrar veículo').click();
  await page.getByLabel('Cliente *', { exact: true }).selectOption({ label: 'Cliente Fase1 navegador' });
  const plate = 'E2E'.replace('2', 'T') + String(Date.now() % 10000).padStart(4, '0');
  await page.getByLabel('Placa *', { exact: true }).fill(plate);
  await page.getByLabel('Marca *', { exact: true }).fill('Fiat');
  await page.getByLabel('Modelo *', { exact: true }).fill('Uno');
  await page.getByLabel('Ano *', { exact: true }).fill('2020');
  await page.getByLabel('Cor *', { exact: true }).fill('Prata');
  await page.getByLabel('Quilometragem *', { exact: true }).fill('1000');
  await button('Salvar').click(); await saved(); step('Veículo persistido pela interface');
  await nav('Ordens de Serviço'); await button('Abrir OS').click();
  await page.getByLabel('Veículo *', { exact: true }).selectOption({ label: `${plate} · Fiat Uno` });
  await page.getByLabel('Quilometragem de entrada *', { exact: true }).fill('1001');
  await page.getByLabel('Relato do cliente *', { exact: true }).fill('Ruído nos freios: teste real da Fase 1.');
  await page.getByLabel('Mecânico responsável', { exact: true }).selectOption({ label: 'Mecânico Fase1' });
  await page.getByRole('dialog').getByRole('button', { name: 'Abrir OS', exact: true }).click(); await saved();
  await visible(page.getByRole('heading', { name: /OS #/ })); step('OS e dados de entrada persistidos');
  await page.getByRole('tab', { name: 'Checklist', exact: true }).click();
  await button('Registrar checklist').click();
  await page.getByLabel('Descrição *', { exact: true }).fill('Pneus');
  await page.getByLabel('Condição *', { exact: true }).fill('Bom estado');
  await page.getByRole('dialog').getByRole('button', { name: 'Registrar checklist', exact: true }).click(); await saved(); step('Checklist real');
  async function status(next) {
    await button('Atualizar status').click(); await page.getByLabel('Próxima etapa *', { exact: true }).selectOption(next);
    await button('Confirmar status').click(); await saved();
  }
  await status('DIAGNOSTICO');
  await page.getByRole('tab', { name: 'Diagnóstico', exact: true }).click(); await button('Adicionar item').click();
  await page.getByLabel('Avaliação técnica *', { exact: true }).fill('Pastilhas gastas.');
  await page.getByLabel('Classificação *', { exact: true }).selectOption('TROCAR');
  await button('Registrar item').click(); await saved(); step('Diagnóstico traduzido para VERMELHO na API');
  await page.getByRole('tab', { name: 'Fotos', exact: true }).click(); await button('Adicionar foto').click();
  // Valid PNG, generated from a canvas by the browser, without external images.
  const png = await page.evaluate(() => { const c = document.createElement('canvas'); c.width = 40; c.height = 30; const ctx = c.getContext('2d'); ctx.fillStyle = '#006644'; ctx.fillRect(0, 0, 40, 30); return c.toDataURL('image/png').split(',')[1]; });
  await page.getByLabel('Foto *', { exact: true }).setInputFiles({ name: 'teste.png', mimeType: 'image/png', buffer: Buffer.from(png, 'base64') });
  await page.getByLabel('Finalidade *', { exact: true }).selectOption('DIAGNOSTICO');
  await page.getByLabel('Descrição', { exact: true }).fill('Foto privada de teste');
  await page.getByLabel('Vincular a um item', { exact: true }).selectOption({ label: 'Diagnóstico · Pastilhas gastas.' });
  await page.getByRole('dialog').getByRole('button', { name: 'Adicionar foto', exact: true }).click(); await saved();
  await visible(page.getByRole('img', { name: 'Foto privada de teste' }));
  assert(await page.getByRole('img', { name: 'Foto privada de teste' }).evaluate(img => img.complete && img.naturalWidth === 40));
  await page.screenshot({ path: `${output}/foto-privada.png`, fullPage: true }); step('Upload MinIO e imagem privada visível no navegador');
  await status('ORCAMENTO');
  await page.getByRole('tab', { name: 'Orçamento', exact: true }).click(); await button('Criar orçamento').click();
  await page.getByLabel('Descrição *', { exact: true }).fill('Substituição de pastilhas');
  await page.getByLabel('Tipo', { exact: true }).selectOption('SERVICO');
  await page.getByLabel('Valor unitário (R$) *', { exact: true }).fill('150.50');
  await button('Criar versão 1').click(); await saved();
  await button('Disponibilizar orçamento').click();
  await visible(page.getByText('Aguardando aprovação', { exact: true }).first());
  await button('Gerar link do cliente').click();
  await visible(page.getByLabel('Link do cliente', { exact: true }));
  const link = await page.getByLabel('Link do cliente', { exact: true }).inputValue();
  secrets.push(new URL(link).hash.slice(1));
  const publicPage = await browser.newPage();
  publicPage.on('pageerror', e => result.errors.push(e.message));
  await publicPage.goto(`${base}/acompanhar${new URL(link).hash}`);
  await publicPage.getByRole('button', { name: /Aprovar R\$/ }).click();
  await publicPage.getByRole('button', { name: 'Confirmar aprovação', exact: true }).click();
  await visible(publicPage.getByText('Aprovação registrada.', { exact: true }));
  await publicPage.close(); step('Versão e aprovação pública reais, em navegador sem sessão');
  await button('Atualizar dados').click();
  await visible(page.getByText('Em manutenção', { exact: true }).first());
  await status('TESTE'); await status('PRONTO');
  await page.getByRole('tab', { name: 'Timeline', exact: true }).click();
  await visible(page.getByText('Cliente aprovou o orçamento v1 pelo link.', { exact: true }));
  await page.screenshot({ path: `${output}/timeline-conclusao.png`, fullPage: true }); step('Status até PRONTO e timeline persistida');
  await page.reload();
  await visible(button('Entrar')); await login(fixture.a);
  await nav('Ordens de Serviço'); await page.getByRole('button', { name: /Abrir OS \d+/ }).first().click();
  await visible(page.getByText('Pronto', { exact: true }).first());
  await page.getByRole('tab', { name: /^Fotos/ }).click();
  await visible(page.getByRole('img', { name: 'Foto privada de teste' })); step('Novo login confirma persistência de OS e foto');
  const json = async (path, token, method = 'GET', body) => {
    const r = await fetch(`${backend}/api/v1${path}`, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
    return { status: r.status, body: await r.json().catch(() => null) };
  };
  const sessionA = (await json('/auth/login', null, 'POST', { oficina: fixture.a.slug, email: fixture.email, senha: fixture.password })).body;
  const sessionB = (await json('/auth/login', null, 'POST', { oficina: fixture.b.slug, email: fixture.email, senha: fixture.password })).body;
  secrets.push(sessionA.accessToken, sessionB.accessToken, sessionA.refreshToken, sessionB.refreshToken);
  const orders = (await json('/ordens-servico', sessionA.accessToken)).body.itens;
  const order = orders.find(o => o.relato === 'Ruído nos freios: teste real da Fase 1.');
  const osPath = `/ordens-servico/${order.id}`;
  const photos = (await json(`${osPath}/fotos`, sessionA.accessToken)).body;
  const photoPath = `${osPath}/fotos/${photos[0].id}/conteudo`;
  for (const path of [osPath, `${osPath}/checklist`, `${osPath}/diagnosticos`, `${osPath}/orcamento/versoes`, `${osPath}/fotos`, `${osPath}/timeline`, photoPath, `/clientes/${order.clienteId}`, `/veiculos/${order.veiculoId}`]) assert.equal((await json(path, sessionB.accessToken)).status, 404);
  assert.equal((await json(photoPath)).status, 401);
  assert.equal((await json(`${osPath}/status`, sessionB.accessToken, 'POST', { status: 'DIAGNOSTICO', revisao: 0 })).status, 404);
  const direct = await fetch(`http://127.0.0.1:9000/garagem-fotos/${fixture.a.id}/${order.id}/${photos[0].id}`);
  assert.equal(direct.status, 403);
  result.persisted = { oficinaId: fixture.a.id, ordemId: order.id, clienteId: order.clienteId, veiculoId: order.veiculoId, fotoId: photos[0].id, status: order.status };
  step('Oficina B bloqueada em todos os recursos; foto sem JWT 401 e URL MinIO anônima 403');
  await button('Abrir perfil').click(); await button('Sair da oficina').click();
  await login(fixture.b); await nav('Ordens de Serviço');
  await visible(page.getByRole('heading', { name: 'Nenhum registro cadastrado', exact: true }));
  assert.equal(await page.getByText('Cliente Fase1 navegador', { exact: true }).count(), 0);
  step('Troca de oficina limpa os dados da sessão anterior');
  for (const width of [834, 390]) {
    await page.setViewportSize({ width, height: 900 });
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
  }
  assert.deepEqual(result.errors, []);
  for (const session of [sessionA, sessionB]) await json('/auth/logout', null, 'POST', { oficinaId: session.oficinaId, refreshToken: session.refreshToken });
  fs.writeFileSync(`${output}/resultado.json`, JSON.stringify(result, null, 2));
  step('E2E aprovado sem mocks e sem erros JavaScript');
} catch (error) {
  let message = String(error.stack || error);
  for (const secret of secrets.filter(Boolean)) message = message.split(secret).join('[REDACTED]');
  console.error(message);
  process.exitCode = 1;
} finally { await browser.close(); }
