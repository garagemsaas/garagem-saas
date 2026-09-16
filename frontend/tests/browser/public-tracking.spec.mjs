import { test, expect } from '@playwright/test';

test('versão substituída exige nova confirmação e preserva comprovante de recusa', async ({ page }) => {
  let current = 1;
  let decided = false;
  const writes = [];
  await page.route('**/api/v1/publico/**', async route => {
    const request = route.request();
    expect(request.headers().authorization).toBeUndefined();
    if (request.method() === 'POST') {
      const body = request.postDataJSON();
      writes.push(body);
      if (body.versaoId !== `v${current}`) return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ detail: 'Orçamento substituído. Confira a nova versão.' }) });
      decided = true;
    }
    await route.fulfill({ contentType: 'application/json', body: JSON.stringify({
      numero: 7, status: decided ? 'ORCAMENTO' : 'AGUARDANDO_APROVACAO', veiculo: 'Fiat Uno', previsaoEntrega: null,
      orcamento: { id: `v${current}`, numero: current, total: current * 10, observacoes: '', criadoEm: '2026-09-16T12:00:00Z',
        decisao: decided ? { aprovado: false, criadoEm: '2026-09-16T13:00:00Z', canal: 'LINK_PUBLICO' } : null,
        itens: [{ id: 'i', tipo: 'SERVICO', descricao: 'Inspeção', quantidade: 1, valorUnitario: current * 10, subtotal: current * 10 }] },
    }) });
  });
  await page.goto(`/acompanhar#${'b'.repeat(43)}`);
  await page.getByRole('button', { name: /Aprovar R/ }).click();
  current = 2;
  await page.getByRole('button', { name: 'Confirmar aprovação', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Orçamento · versão 2' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Confirmar aprovação', exact: true })).toHaveCount(0);
  expect(writes).toEqual([{ versaoId: 'v1', aprovado: true }]);
  await page.getByRole('button', { name: 'Recusar orçamento', exact: true }).click();
  await page.getByRole('button', { name: 'Confirmar recusa', exact: true }).click();
  await expect(page.getByText(/Orçamento recusado em/)).toBeVisible();
  await page.reload();
  await expect(page.getByText(/Orçamento recusado em/)).toBeVisible();
  await expect(page.getByRole('button', { name: /Aprovar R/ })).toHaveCount(0);
  expect(writes).toEqual([{ versaoId: 'v1', aprovado: true }, { versaoId: 'v2', aprovado: false }]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
});

for (const action of ['atualizar', 'decidir']) {
  test(`link revogado ao ${action} remove orçamento e ações`, async ({ page }) => {
    let revoked = false;
    await page.route('**/api/v1/publico/**', async route => {
      expect(route.request().headers().authorization).toBeUndefined();
      await route.fulfill({ status: revoked ? 404 : 200, contentType: 'application/json', body: JSON.stringify(revoked ? {
        code: 'NOT_FOUND', detail: 'Acesso inválido ou expirado.',
      } : {
        numero: 1, status: 'AGUARDANDO_APROVACAO', veiculo: 'Fiat Uno', previsaoEntrega: null,
        orcamento: { id: 'versao', numero: 1, total: 10, observacoes: null, criadoEm: '2026-09-16T12:00:00Z', decisao: null,
          itens: [{ id: 'item', tipo: 'SERVICO', descricao: 'Inspeção', quantidade: 1, valorUnitario: 10, subtotal: 10 }] },
      }) });
    });
    await page.goto(`/acompanhar#${'a'.repeat(43)}`);
    await expect(page.getByRole('button', { name: /Aprovar R/ })).toBeVisible();
    revoked = true;
    if (action === 'atualizar') await page.getByRole('button', { name: 'Atualizar acompanhamento' }).click();
    else {
      await page.getByRole('button', { name: /Aprovar R/ }).click();
      await page.getByRole('button', { name: 'Confirmar aprovação', exact: true }).click();
    }
    await expect(page.getByRole('heading', { name: 'Link indisponível' })).toBeVisible();
    await expect(page.getByText('Inspeção', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Aprovar R/ })).toHaveCount(0);
  });
}
