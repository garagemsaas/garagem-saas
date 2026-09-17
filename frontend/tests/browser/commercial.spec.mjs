import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

async function accessible(page) {
  const report = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(report.violations.map(v => ({ id: v.id, nodes: v.nodes.map(n => n.target) }))).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
}
test('institucional pública, proposta honesta, downloads e ajuda sem API', async ({ page }, info) => {
  const calls = [];
  await page.route('**/api/v1/**', route => { calls.push(route.request().url()); return route.abort(); });
  await page.goto('/institucional');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Sua oficina já tem clientes.');
  await accessible(page);
  await page.screenshot({ path: test.info().outputPath(`institucional-${info.project.name}.png`), fullPage: true });
  await page.getByRole('link', { name: 'Ver a proposta', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Condições sob consulta' })).toBeInViewport();
  const deck = await page.request.get('/materiais/garagem-apresentacao.pptx');
  expect(deck.status()).toBe(200); expect((await deck.body()).subarray(0, 2).toString()).toBe('PK');
  const video = await page.request.get('/materiais/garagem-demonstracao.webm');
  expect(video.status()).toBe(200); expect((await video.body()).subarray(0, 4).toString('hex')).toBe('1a45dfa3');
  const captions = await page.request.get('/materiais/garagem-demonstracao.vtt');
  expect(await captions.text()).toContain('WEBVTT');
  const player = page.locator('video');
  await player.evaluate(v => v.load());
  await expect.poll(() => player.evaluate(v => v.readyState)).toBeGreaterThanOrEqual(2);
  expect(await player.evaluate(v => v.duration)).toBeGreaterThan(45);
  await page.goto('/ajuda');
  await page.getByText('Como envio um orçamento?', { exact: true }).click();
  await expect(page.getByText('Disponibilizar ou copiar não envia uma mensagem.', { exact: false })).toBeVisible();
  await accessible(page); expect(calls).toEqual([]);
  await page.getByRole('link', { name: 'Acessar minha oficina', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Entre na sua oficina' })).toBeVisible();
});

for (const role of ['OWNER', 'ATENDENTE', 'MECANICO']) {
  test(`preparação remota respeita perfil ${role} e recupera erro`, async ({ page }) => {
    let fail = false; let exists = false;
    const calls = [];
    await page.route('**/api/v1/**', async route => {
      const request = route.request(), path = new URL(request.url()).pathname;
      calls.push({ path, method: request.method() });
      const send = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
      if (path.endsWith('/auth/login')) return send({ accessToken: 'a', refreshToken: 'r', oficinaId: 'a', usuarioId: 'u', nome: 'Teste', papel: role, expiresIn: 900 });
      if (path.endsWith('/dashboard')) return send({ emAndamento: 0, prontas: 0, porStatus: {}, orcamentosAguardandoDecisao: { total: 0, quantidade: 0 } });
      if (fail) return send({ detail: 'Falha de consulta' }, 503);
      return send({ itens: [], pagina: 0, tamanho: 10, total: exists ? 2 : 0 });
    });
    await page.goto('/');
    await page.getByLabel('Oficina', { exact: true }).fill('a');
    await page.getByLabel('E-mail', { exact: true }).fill('a@example.test');
    await page.getByLabel('Senha', { exact: true }).fill('teste');
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Um dia bem organizado.' })).toBeVisible();
    const menu = page.getByRole('button', { name: 'Abrir menu', exact: true });
    if (await menu.isVisible()) await menu.click();
    calls.length = 0;
    fail = role !== 'MECANICO';
    await page.getByRole('button', { name: 'Primeiros passos', exact: true }).click();
    const section = page.getByRole('region', { name: 'Preparação inicial da oficina' });
    if (role === 'MECANICO') {
      await expect(section).toHaveCount(0); expect(calls).toEqual([]);
    } else {
      await expect(section.getByRole('heading', { name: 'Não foi possível verificar a preparação' })).toBeVisible();
      fail = false;
      await section.getByRole('button', { name: 'Tentar novamente' }).click();
      await expect(section.getByText('Nenhum registro encontrado', { exact: true })).toHaveCount(3);
      await accessible(page);
      expect(calls.every(c => c.method === 'GET')).toBe(true);
      expect(calls.some(c => c.path.endsWith('/usuarios'))).toBe(role === 'OWNER');
      await page.getByRole('button', { name: 'Fechar painel', exact: true }).click();
      exists = true;
      if (await menu.isVisible()) await menu.click();
      await page.getByRole('button', { name: 'Primeiros passos', exact: true }).click();
      await expect(section.getByText('Registro encontrado — confira os dados', { exact: true })).toHaveCount(role === 'OWNER' ? 4 : 3);
      await section.getByRole('button', { name: 'Ir para veículos', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Veículos', exact: true })).toBeVisible();
    }
  });
}
