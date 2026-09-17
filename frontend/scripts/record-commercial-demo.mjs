// Recording-only fixtures. Never imported by the app. All API calls stay in this browser.
// Run with Vite on 127.0.0.1:5181: node scripts/record-commercial-demo.mjs
import { chromium } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

const output = path.resolve('public/materiais');
const temporary = path.resolve('../.tools/fase7-video');
await fs.mkdir(output, { recursive: true });
await fs.mkdir(temporary, { recursive: true });
const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, recordVideo: { dir: temporary, size: { width: 1280, height: 900 } } });
const customer = { id: 'c1', nome: 'Cliente de demonstração', telefone: 'Não informado', email: null, revisao: 0 };
const vehicle = { id: 'v1', clienteId: 'c1', marca: 'Fiat', modelo: 'Uno', placa: 'DEM0A00', ano: 2020, km: 30000, cor: 'Prata', revisao: 0 };
const order = { id: 'os1', numero: 42, clienteId: 'c1', veiculoId: 'v1', status: 'AGUARDANDO_APROVACAO', prioridade: 'NORMAL', mecanicoId: null, relato: 'Exemplo de revisão', revisao: 0, criadoEm: '2026-09-01T12:00:00Z', previsaoEntrega: null };
const opportunity = { id: 'op1', tipo: 'ORCAMENTO_ESQUECIDO', status: 'ABERTA', revisao: 0,
  cliente: customer, veiculo: vehicle, origem: { ordemServicoId: 'os1', numeroOs: 42, orcamentoVersaoId: 'exemplo-v1' }, responsavel: null,
  valorPotencial: 1200, criadoEm: '2026-09-17T12:00:00Z', elegivelDesde: '2026-09-10T12:00:00Z', diasEmAberto: 7,
  ultimoContatoEm: null, proximoContatoEm: null, encerradaEm: null };
await context.route('**/api/v1/**', route => {
  const url = new URL(route.request().url()), endpoint = url.pathname.replace('/api/v1', '');
  const page = itens => ({ itens, pagina: 0, tamanho: 10, total: itens.length });
  let body;
  if (endpoint === '/auth/login') body = { accessToken: 'demo-only', refreshToken: 'demo-only', oficinaId: 'demo', usuarioId: 'demo', nome: 'Gestor de demonstração', papel: 'OWNER', expiresIn: 900 };
  else if (endpoint === '/dashboard') body = { emAndamento: 1, prontas: 0, porStatus: { AGUARDANDO_APROVACAO: 1 }, orcamentosAguardandoDecisao: { total: 1200, quantidade: 1 } };
  else if (endpoint === '/clientes') body = page([customer]);
  else if (endpoint === '/veiculos') body = page([vehicle]);
  else if (endpoint === '/clientes/c1') body = customer;
  else if (endpoint === '/veiculos/v1') body = vehicle;
  else if (endpoint === '/usuarios') body = page([{ id: 'demo', nome: 'Gestor de demonstração', email: 'demo@example.test', papel: 'OWNER', ativo: true }]);
  else if (endpoint === '/ordens-servico') body = page([order]);
  else if (endpoint.endsWith('/resumo')) body = { oportunidadesAbertas: 1, valorPotencialConhecido: 1200, valorRecuperado: 0, quantidadeRecuperada: 0 };
  else if (endpoint.endsWith('/oportunidades')) body = page([opportunity]);
  else if (endpoint.endsWith('/oportunidades/op1')) body = { oportunidade: opportunity, contatos: [], resultado: null, auditoria: [] };
  else return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ detail: 'Fora do roteiro de demonstração.' }) });
  return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
});
const page = await context.newPage();
page.setDefaultTimeout(15000);
const start = performance.now();
const captions = [];
async function scene(text, seconds = 6) {
  const begin = (performance.now() - start) / 1000;
  await page.evaluate(text => {
    let label = document.getElementById('demo-caption');
    if (!label) { label = document.createElement('div'); label.id = 'demo-caption'; document.body.append(label); }
    label.style.cssText = 'position:fixed;bottom:0;left:0;right:0;z-index:2147483647;padding:16px 24px;background:#233d34;color:white;font:16px Inter,Arial,sans-serif;pointer-events:none;text-align:center;line-height:1.6';
    label.textContent = `DEMONSTRAÇÃO FICTÍCIA · ${text}`;
    // Dialog top layer would cover body captions. Keep the caption in the active dialog.
    const dialog = document.querySelector('dialog[open]');
    (dialog ?? document.body).append(label);
  }, text);
  await page.waitForTimeout(seconds * 1000);
  captions.push({ begin, end: (performance.now() - start) / 1000, text: `Demonstração fictícia. ${text}` });
}
try {
  await page.goto('http://127.0.0.1:5181/');
  await page.getByLabel('Oficina', { exact: true }).fill('oficina-demonstracao');
  await page.getByLabel('E-mail', { exact: true }).fill('demo@example.test');
  await page.getByLabel('Senha', { exact: true }).fill('demonstracao');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await page.getByRole('heading', { name: 'Um dia bem organizado.' }).waitFor();
  await scene('O painel reúne a operação da oficina. Esta base usa somente exemplos.');
  await page.screenshot({ path: path.join(temporary, 'poster.png') });
  await page.getByRole('button', { name: 'Primeiros passos', exact: true }).click();
  await page.getByText('Preparação inicial da oficina', { exact: true }).waitFor();
  await scene('Primeiros passos consulta os cadastros e orienta a preparação inicial.', 7);
  await page.getByRole('button', { name: 'Ir para clientes', exact: true }).click();
  await page.getByRole('heading', { name: 'Clientes', exact: true }).waitFor();
  await scene('O cliente vem primeiro. Depois, vincule o veículo e abra a OS.');
  await page.getByRole('button', { name: 'Dinheiro Esquecido', exact: true }).click();
  await page.getByRole('button', { name: 'Ver oportunidade de Cliente de demonstração' }).waitFor();
  await scene('Dinheiro Esquecido mostra oportunidades e o valor potencial. Aqui, R$ 1.200 fictícios.', 7);
  await page.getByRole('button', { name: 'Ver oportunidade de Cliente de demonstração' }).click();
  await page.getByRole('button', { name: 'Registrar contato', exact: true }).waitFor();
  await scene('Confira a origem do valor e a próxima ação antes de falar com o cliente.', 7);
  await page.getByRole('button', { name: 'Registrar contato', exact: true }).click();
  await page.getByLabel('Canal', { exact: true }).waitFor();
  await scene('Depois da conversa, registre o canal e o resultado. As mensagens são enviadas manualmente.', 7);
  await scene('Valor potencial não é receita. Confirme o resultado antes de marcar recuperação.', 6);
  await scene('Garagem Oficina. Preço, limites e implantação sob consulta com quem apresentou o produto.', 6);
} finally {
  await context.close();
  await browser.close();
}
await page.video().saveAs(path.join(output, 'garagem-demonstracao.webm'));
await fs.copyFile(path.join(temporary, 'poster.png'), path.join(output, 'garagem-demonstracao.png'));
const timestamp = seconds => new Date(Math.round(seconds * 1000)).toISOString().slice(11, 23);
await fs.writeFile(path.join(output, 'garagem-demonstracao.vtt'), 'WEBVTT\n\n' + captions.map((c, i) => `${i + 1}\n${timestamp(c.begin)} --> ${timestamp(c.end)}\n${c.text}\n`).join('\n'));
console.log('Vídeo sem áudio, legendado e com exemplos fictícios:', output);
