import { empresa } from './company-fixture.mjs';
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

async function login(page, role) {
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    // Sem cookie de sessão, a retomada do carregamento é recusada — como num navegador limpo.
    if (path.endsWith('/auth/refresh')) return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ detail: 'Sessão expirada.' }) });
    const body = path.endsWith('/empresa') ? empresa : path.endsWith('/auth/login')
      ? { accessToken: 'test', refreshToken: 'test', oficinaId: 'pilot-a', usuarioId: 'u', nome: 'Participante', papel: role, expiresIn: 900 }
      : path.endsWith('/dashboard')
        ? { emAndamento: 0, prontas: 0, porStatus: {}, orcamentosAguardandoDecisao: { total: 0, quantidade: 0 } }
        : { itens: [], pagina: 0, tamanho: 10, total: 0 };
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
  });
  await page.goto('/');
  await page.getByLabel('Empresa', { exact: true }).fill('piloto-a');
  await page.getByLabel('E-mail', { exact: true }).fill('piloto@example.test');
  await page.getByLabel('Senha', { exact: true }).fill('teste');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Início', level: 1 })).toBeVisible();
}
// "Primeiros passos" é um guia de estreia e saiu do menu de trabalho: mora dentro da conta, a um
// clique, onde não compete diariamente com clientes, veículos e serviços.
async function openGuide(page) {
  // O acesso à conta vive no cabeçalho, igual nos três tamanhos — não dentro do menu lateral.
  await page.getByRole('button', { name: 'Abrir perfil', exact: true }).click();
  await page.getByRole('button', { name: 'Primeiros passos', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Primeiros passos' })).toBeVisible();
}

for (const role of ['OWNER', 'ATENDENTE', 'MECANICO']) {
  test(`guia acessível, atalhos e limites do perfil ${role}`, async ({ page }, info) => {
    await login(page, role);
    await openGuide(page);
    const guide = page.getByRole('dialog', { name: 'Primeiros passos' });
    if (role === 'MECANICO') {
      await expect(guide.getByRole('button', { name: 'Abrir clientes', exact: true })).toHaveCount(0);
      await expect(guide.getByRole('button', { name: 'Abrir retornos', exact: true })).toHaveCount(0);
    } else {
      await expect(guide.getByText('Disponibilizar não envia uma mensagem.', { exact: false })).toBeVisible();
      await guide.getByRole('button', { name: 'Abrir clientes', exact: true }).click();
      await expect(page.getByRole('heading', { name: 'Clientes', exact: true })).toBeVisible();
      await expect(page.getByRole('dialog')).toHaveCount(0);
      await openGuide(page);
    }
    // O guia deixou de anunciar funções futuras: o que resta é o que fazer quando algo falha.
    await expect(guide.getByRole('heading', { name: 'Se uma ação falhar' })).toBeVisible();
    await expect(guide.getByText('Em breve', { exact: false })).toHaveCount(0);
    const report = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
    expect(report.violations).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
    await guide.screenshot({ path: test.info().outputPath(`guia-${role}-${info.project.name}.png`) });
    await guide.getByRole('button', { name: 'Abrir serviços', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Serviços', exact: true })).toBeVisible();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page.locator('#main-content')).toBeFocused();
    await openGuide(page);
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });
}
