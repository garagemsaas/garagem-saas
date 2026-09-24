import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { empresa } from './company-fixture.mjs';
for (const role of ['DESENVOLVEDOR', 'ADMIN_PLATAFORMA']) {
  test(`administração de empresas e identidade para ${role}`, async ({ page }, testInfo) => {
    const rows = []; const writes = [];
    await page.route('**/api/v1/**', route => {
      const req = route.request(), path = new URL(req.url()).pathname;
      const send = (body, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
      expect(path).toContain('/plataforma/');
      if (path.endsWith('/auth/login')) return send({ accessToken: 'platform', nome: 'Equipe', papel: role });
      expect(req.headers().authorization).toBe('Bearer platform');
      if (path.endsWith('/auth/logout')) return route.fulfill({ status: 204 });
      if (req.method() !== 'GET') {
        const body = req.postDataJSON();writes.push({ path, body });
        if (path.endsWith('/identidade')) Object.assign(rows[0].identidade.branding, body);
        else if (req.method() === 'POST') rows.push({ empresa: { ...body, id: 'e1', revisao: 0, status: 'ATIVA' }, identidade: structuredClone(empresa) });
        else Object.assign(rows[0].empresa, body, { revisao: rows[0].empresa.revisao + 1 });
        return send(rows[0], req.method() === 'POST' ? 201 : 200);
      }
      if (path.endsWith('/empresas')) return send({ itens: rows.map(r => r.empresa), total: rows.length });
      if (path.endsWith('/auditoria')) return send([]);
      return send(rows[0]);
    });
    await page.goto('/administracao');await page.getByLabel('E-mail', { exact: true }).fill('equipe@example.test');await page.getByLabel('Senha', { exact: true }).fill('SenhaSegura123!');await page.getByRole('button', { name: 'Entrar', exact: true }).click();
    await page.getByRole('button', { name: 'Cadastrar empresa', exact: true }).click();
    await page.getByLabel('Nome empresarial').fill('Empresa testada');await page.getByLabel('Identificador para entrar na empresa').fill('empresa-testada');await page.getByLabel('Nome do proprietário').fill('Ana');await page.getByLabel('E-mail do proprietário').fill('ana@example.test');await page.getByLabel('Senha inicial').fill('SenhaSegura123!');await page.getByRole('button', { name: 'Salvar', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Empresa testada', exact: true })).toBeVisible();
    if (role === 'DESENVOLVEDOR') {
      await page.getByRole('button', { name: 'Identidade', exact: true }).click();await page.getByLabel('Nome exibido').fill('Nome visível');await page.getByRole('button', { name: 'Salvar', exact: true }).click();await expect.poll(() => rows[0].identidade.branding.nomeExibicao).toBe('Nome visível');
    } else await expect(page.getByRole('button', { name: 'Identidade', exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: 'Administrar', exact: true }).click();await page.getByLabel('Situação', { exact: true }).selectOption('SUSPENSA');await page.getByLabel('Motivo da alteração').fill('Solicitação do responsável');await page.getByRole('button', { name: 'Salvar', exact: true }).click();await page.getByRole('button', { name: 'Confirmar e salvar' }).click();await expect(page.getByText('Suspensa', { exact: true })).toBeVisible();
    expect((await new AxeBuilder({ page }).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze()).violations).toEqual([]);expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('administracao.png'), fullPage: true });
    expect(writes).toHaveLength(role === 'DESENVOLVEDOR' ? 3 : 2);await page.getByRole('button', { name: 'Sair', exact: true }).click();await expect(page.getByRole('heading', { name: 'Administração da plataforma' })).toBeVisible();
  });
}
test('rotas removidas não abrem funções antigas', async ({ page }) => { for (const path of ['/site/empresa', '/institucional', '/revenda']) { await page.goto(path);await expect(page.getByRole('heading', { name: /encontrada/ })).toBeVisible(); } });
