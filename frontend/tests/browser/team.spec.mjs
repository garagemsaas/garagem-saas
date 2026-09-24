import { test, expect } from '@playwright/test';
import { empresa } from './company-fixture.mjs';
import AxeBuilder from '@axe-core/playwright';
test('proprietário cadastra, edita e desativa a equipe', async ({ page }) => {
  const users = [{ id: 'u', nome: 'Ana', email: 'ana@example.test', papel: 'OWNER', ativo: true }];
  await page.route('**/api/v1/**', route => {
    const req = route.request(), path = new URL(req.url()).pathname.replace('/api/v1', '');
    const send = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
    if (path === '/auth/refresh') return send({}, 401);
    if (path === '/auth/login') return send({ accessToken: 'a', oficinaId: 'e', usuarioId: 'u', nome: 'Ana', papel: 'OWNER', expiresIn: 900 });
    if (path === '/empresa') return send(empresa);
    if (path === '/dashboard') return send({ emAndamento: 0, prontas: 0, porStatus: {}, orcamentosAguardandoDecisao: { total: 0, quantidade: 0 } });
    if (path.startsWith('/usuarios') && req.method() !== 'GET') {
      const body = req.postDataJSON();
      if (req.method() === 'POST') users.push({ id: 'u2', ...body, ativo: true });else Object.assign(users[1], body);
      return send(users[1], req.method() === 'POST' ? 201 : 200);
    }
    const itens = path === '/usuarios' ? users : [];return send({ itens, total: itens.length, pagina: 0, tamanho: 10 });
  });
  await page.goto('/');await page.getByLabel('Empresa', { exact: true }).fill('empresa');await page.getByLabel('E-mail', { exact: true }).fill('ana@example.test');await page.getByLabel('Senha', { exact: true }).fill('senha');await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Início', level: 1 })).toBeVisible();const menu = page.getByRole('button', { name: 'Abrir menu', exact: true });if (await menu.isVisible()) await menu.click();await page.getByRole('button', { name: 'Usuários', exact: true }).click();
  await page.getByRole('button', { name: 'Adicionar usuário', exact: true }).click();await page.getByLabel('Nome', { exact: true }).fill('Bruno');await page.getByLabel('E-mail', { exact: true }).fill('bruno@example.test');await page.getByLabel('Senha inicial', { exact: true }).fill('SenhaSegura123!');await page.getByRole('button', { name: 'Salvar', exact: true }).click();
  const bruno = page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'Bruno', exact: true }) });await expect(bruno).toBeVisible();await bruno.getByRole('button', { name: 'Editar usuário' }).click();await page.getByLabel('Nome', { exact: true }).fill('Bruno Silva');await page.getByRole('button', { name: 'Salvar', exact: true }).click();
  const edited = page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'Bruno Silva', exact: true }) });await edited.getByRole('button', { name: 'Desativar acesso' }).click();await page.getByRole('button', { name: 'Salvar', exact: true }).click();await page.getByRole('button', { name: 'Confirmar e salvar' }).click();await expect(edited.getByText(/Inativo/)).toBeVisible();
  expect((await new AxeBuilder({ page }).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze()).violations).toEqual([]);expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
});
