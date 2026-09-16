import { test, expect } from '@playwright/test';

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
