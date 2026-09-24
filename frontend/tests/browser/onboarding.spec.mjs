import { empresa } from './company-fixture.mjs';
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

async function login(page, role) {
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    // Sem cookie de sessão, a retomada do carregamento é recusada — como num navegador limpo.
    if (path.endsWith('/auth/refresh')) return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ detail: 'Sessão expirada.' }) });
    const body = path.endsWith('/empresa') ? empresa : path.endsWith('/auth/login')
      ? { accessToken: 'test', refreshToken: 'test', oficinaId: 'empresa-a', usuarioId: 'u', nome: 'Participante', papel: role, expiresIn: 900 }
      : path.endsWith('/dashboard')
        ? { emAndamento: 0, prontas: 0, porStatus: {}, orcamentosAguardandoDecisao: { total: 0, quantidade: 0 } }
        : { itens: [], pagina: 0, tamanho: 10, total: 0 };
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.goto('/');
  await page.getByLabel('Empresa', { exact: true }).fill('empresa-a');
  await page.getByLabel('E-mail', { exact: true }).fill('empresa@example.test');
  await page.getByLabel('Senha', { exact: true }).fill('teste');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Início', level: 1 })).toBeVisible();
}
for (const role of ['OWNER', 'ATENDENTE', 'MECANICO']) {
 test(`FAQ acessivel para ${role}`, async ({ page }) => {
  await login(page,role);
  await page.getByRole('button',{name:'Abrir perfil',exact:true}).click();
  await page.getByRole('button',{name:'Como podemos ajudar?',exact:true}).click();
  const guide=page.getByRole('dialog',{name:'Como podemos ajudar?'});
  await expect(guide).toBeVisible();
  await guide.locator('summary').first().click();
  await expect(guide.getByText('Abra Clientes', {exact:false})).toBeVisible();
  expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa']).analyze()).violations).toEqual([]);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1)).toBe(true);
  await page.keyboard.press('Escape');await expect(guide).toHaveCount(0);
 });
}
