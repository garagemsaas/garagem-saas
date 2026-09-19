import { test, expect } from '@playwright/test';
import { branding } from './company-fixture.mjs';

test('identidade acompanha sessão e OWNER salva apenas configuração permitida', async ({ page }) => {
  let current = 'A'; const writes = [];
  const companies = {
    A: { branding: { ...branding, nomeExibicao: 'Empresa Alfa', corPrimaria: '#123456', logoId: 'logo-a', faviconId: 'favicon-a' }, modulos: ['OFICINA'], status: 'ATIVA' },
    B: { branding: { ...branding, nomeExibicao: 'Empresa Beta', corPrimaria: '#654321' }, modulos: ['REVENDA'], status: 'ATIVA' },
  };
  await page.route('**/api/v1/**', route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    const send = body => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    if (path.endsWith('/auth/login')) {
      current = request.postDataJSON().oficina;
      return send({ accessToken: current, refreshToken: current, oficinaId: current, usuarioId: current, nome: 'Owner', papel: 'OWNER', expiresIn: 900 });
    }
    if (path.endsWith('/auth/logout')) return route.fulfill({ status: 204 });
    expect(request.headers().authorization).toBe(`Bearer ${current}`);
    if (path.includes('/empresa/imagens/')) {
      expect(current).toBe('A');
      return route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', 'base64') });
    }
    if (path.endsWith('/empresa')) {
      if (request.method() === 'PUT') {
        const body = request.postDataJSON(); writes.push(body);
        companies[current].branding = { ...companies[current].branding, ...body, revisao: body.revisao + 1 };
      }
      return send(companies[current]);
    }
    expect(current).toBe('A'); // REVENDA não consulta endpoints de OFICINA.
    if (path.endsWith('/dashboard')) return send({ emAndamento: 0, prontas: 0, porStatus: {}, orcamentosAguardandoDecisao: { total: 0, quantidade: 0 } });
    return send({ itens: [], pagina: 0, tamanho: 10, total: 0 });
  });
  async function login(company) {
    await page.getByLabel('Empresa', { exact: true }).fill(company);
    await page.getByLabel('E-mail', { exact: true }).fill('owner@test.local');
    await page.getByLabel('Senha', { exact: true }).fill('senha');
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  }
  await page.goto('/'); await login('A');
  await expect(page).toHaveTitle('Empresa Alfa');
  await expect(page.locator('img.company-logo').first()).toHaveAttribute('src', /^blob:/);
  await expect(page.locator('link[rel="icon"]')).toHaveAttribute('href', /^blob:/);
  expect(await page.evaluate(() => document.documentElement.style.getPropertyValue('--accent'))).toBe('#123456');
  const menu = page.getByRole('button', { name: 'Abrir menu', exact: true });
  if (await menu.isVisible()) await menu.click();
  await page.getByRole('button', { name: 'Configurações', exact: true }).click();
  await page.getByRole('button', { name: 'Identidade da empresa', exact: true }).click();
  await page.getByLabel('Nome de exibição').fill('Alfa Automotiva');
  await page.getByRole('button', { name: 'Salvar identidade' }).click();
  await expect(page).toHaveTitle('Alfa Automotiva');
  expect(writes[0]).not.toHaveProperty('oficinaId'); expect(writes[0]).not.toHaveProperty('modulos'); expect(writes[0]).not.toHaveProperty('status');
  await page.getByRole('button', { name: 'Fechar painel', exact: true }).click();
  if (await menu.isVisible()) await menu.click();
  await page.getByRole('button', { name: 'Configurações', exact: true }).click();
  await page.getByRole('button', { name: 'Sair da oficina', exact: true }).click();
  await expect(page).toHaveTitle(/Plataforma Automotiva/);
  await expect(page.locator('link[rel="icon"]')).toHaveAttribute('href', '/favicon.svg');
  await login('B'); await expect(page).toHaveTitle('Empresa Beta');
  await expect(page.getByText('Nenhum módulo operacional está disponível', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Ordens de serviço', exact: true })).toHaveCount(0);
  await expect(page.locator('img.company-logo')).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.style.getPropertyValue('--accent'))).toBe('#654321');
});

test('página pública recebe branding pelo token sem autoridade de tenant no navegador', async ({ page }) => {
  const calls = [];
  await page.route('**/api/v1/publico/**', route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    calls.push(path); expect(request.headers().authorization).toBeUndefined();
    const body = path.endsWith('/empresa') ? { ...branding, nomeExibicao: 'Empresa do token', corPrimaria: '#123456' }
      : { numero: 1, status: 'RECEBIDO', veiculo: 'Veículo', previsaoEntrega: null, orcamento: null };
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.goto(`/acompanhar?oficinaId=intruso#${'a'.repeat(43)}`);
  await expect(page).toHaveTitle('Empresa do token');
  await expect(page.getByText('Empresa do token', { exact: true })).toBeVisible();
  expect(calls).toContain(`/api/v1/publico/${'a'.repeat(43)}/empresa`);
  expect(calls.some(path => path.includes('intruso'))).toBe(false);
});
