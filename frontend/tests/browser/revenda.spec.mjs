import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { branding } from './company-fixture.mjs';

/**
 * A revenda é o mesmo produto, com outro módulo ligado. O que estes testes protegem não é cada tela
 * isolada, é a fronteira: uma empresa só de oficina não pode ver estoque, uma empresa só de revenda
 * não pode ver ordem de serviço, e a híbrida precisa transitar entre os dois sem trocar de sessão.
 *
 * Nenhum fixture daqui é importado pelo código de produção.
 */
const session = { accessToken: 'test-access', refreshToken: 'test-refresh', oficinaId: 'empresa', usuarioId: 'vendedor', nome: 'Vendedor', papel: 'OWNER', expiresIn: 900 };
const resumo = { emAvaliacao: 1, emPreparacao: 2, disponiveis: 3, reservados: 1, vendidos: 4, valorAquisicao: 210000, custosPreparacao: 4300, valorAnunciado: 260000, margemPotencial: 45700, mediaDiasEstoque: 12.5, leadsAbertos: 5, propostasAbertas: 2, vendasPeriodo: 4, valorVendido: 320000, margemRealizada: 28000 };

function pagina(itens) { return { itens, pagina: 0, tamanho: 10, total: itens.length, totalPaginas: 1 }; }

/** Estado mínimo de uma revenda em operação, suficiente para as telas terem o que mostrar. */
function estado() {
  const veiculo = { id: 'veiculo-1', marca: 'Fiat', modelo: 'Argo', placa: 'ABC1D23', ano: 2021, anoModelo: 2022, km: 30000, cor: 'Prata', propriedade: 'EMPRESA', clienteId: null, revisao: 0 };
  const item = { id: 'estoque-1', veiculoId: veiculo.id, marca: 'Fiat', modelo: 'Argo', placa: 'ABC1D23', ano: 2021, cor: 'Prata', status: 'DISPONIVEL', entrada: '2026-09-01', origem: 'COMPRA', valorAquisicao: 60000, precoAnunciado: 72000, precoMinimo: 66000, custoTotal: 61500, margemPrevista: 10500, responsavelId: 'vendedor', revisao: 0, observacoes: null, avaliacaoId: null, ordemServicoId: null };
  return {
    escritas: [],
    estoque: [item],
    veiculos: [veiculo],
    clientes: [{ id: 'cliente-1', nome: 'Compradora Ana', telefone: '11999999999', revisao: 0 }],
    usuarios: [{ id: 'vendedor', nome: 'Vendedor', email: 'v@test.local', papel: 'OWNER', ativo: true }],
    leads: [{ id: 'lead-1', clienteId: 'cliente-1', clienteNome: 'Compradora Ana', veiculoId: 'veiculo-1', veiculoDescricao: 'Fiat Argo ABC1D23', vendedorId: 'vendedor', origem: 'WHATSAPP', status: 'NOVO', observacoes: null, revisao: 0, criadoEm: '2026-09-10T12:00:00Z' }],
    propostas: [{ id: 'proposta-1', estoqueId: 'estoque-1', clienteId: 'cliente-1', clienteNome: 'Compradora Ana', veiculoDescricao: 'Fiat Argo ABC1D23', vendedorId: 'vendedor', leadId: 'lead-1', status: 'ENVIADA', numeroVersao: 1, precoAnunciado: 72000, valorNegociado: 70000, desconto: 2000, entrada: 5000, valorTroca: 0, avaliacaoTrocaId: null, validade: '2026-12-31T23:59:00Z', observacoes: null, revisao: 0, criadoEm: '2026-09-11T12:00:00Z' }],
    avaliacoes: [{ id: 'avaliacao-1', veiculoId: 'veiculo-1', veiculoDescricao: 'Fiat Argo ABC1D23', clienteId: 'cliente-1', clienteNome: 'Compradora Ana', avaliadorId: 'vendedor', data: '2026-09-05', km: 30000, valorEstimado: 30000, valorOferecido: 28000, validade: '2026-12-31T23:59:00Z', status: 'ABERTA', observacoes: null, revisao: 0 }],
    reservas: [],
    vendas: [{ id: 'venda-1', estoqueId: 'estoque-1', veiculoId: 'veiculo-1', veiculoDescricao: 'Fiat Argo ABC1D23', clienteId: 'cliente-1', clienteNome: 'Compradora Ana', vendedorId: 'vendedor', precoAnunciado: 72000, valorVendido: 70000, desconto: 2000, entrada: 5000, valorTroca: 0, custoAcumulado: 61500, margemBruta: 8500, criadoEm: '2026-09-12T12:00:00Z', observacoes: null }],
  };
}

async function fixture(context, modulos, state = estado()) {
  await context.route('**/api/v1/**', async route => {
    const req = route.request();
    const url = new URL(req.url());
    const path = url.pathname.replace('/api/v1', '');
    const send = (data, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });
    if (path === '/auth/login') return send(session);
    if (path === '/auth/logout') return route.fulfill({ status: 204 });
    // Navegador limpo não tem cookie de sessão: a tentativa de retomada no carregamento é 401.
    if (path === '/auth/refresh') return send({ detail: 'Sessão expirada.' }, 401);
    expect(req.headers().authorization).toBe('Bearer test-access');
    if (path === '/empresa') return send({ branding, modulos, status: 'ATIVA' });
    if (path.startsWith('/revenda/dashboard')) return send(resumo);
    if (req.method() !== 'GET') { state.escritas.push({ path, body: req.postDataJSON?.() ?? null }); return send({ id: 'novo-1', revisao: 1 }, path.endsWith('/status') ? 200 : 201); }
    if (path.startsWith('/revenda/historico') || path.startsWith('/revenda/fotos')) return send(pagina([]));
    for (const [rota, chave] of [['/revenda/estoque', 'estoque'], ['/revenda/leads', 'leads'], ['/revenda/propostas', 'propostas'], ['/revenda/avaliacoes', 'avaliacoes'], ['/revenda/reservas', 'reservas'], ['/revenda/vendas', 'vendas'], ['/clientes', 'clientes'], ['/veiculos', 'veiculos'], ['/usuarios', 'usuarios']]) {
      if (path !== rota && !path.startsWith(`${rota}/`)) continue;
      const resto = path.slice(rota.length).replace(/^\//, '');
      if (!resto) return send(pagina(state[chave]));
      const encontrado = state[chave].find(x => x.id === resto);
      return encontrado ? send(encontrado) : send({ detail: 'Recurso não encontrado.', code: 'NOT_FOUND' }, 404);
    }
    if (path === '/dashboard') return send({ emAndamento: 0, prontas: 0, porStatus: {}, orcamentosAguardandoDecisao: { total: 0, quantidade: 0 } });
    if (path === '/ordens-servico') return send(pagina([]));
    return send({ detail: 'Recurso não encontrado.', code: 'NOT_FOUND' }, 404);
  });
  return state;
}

async function entrar(page) {
  await page.goto('/');
  await page.getByLabel('Empresa', { exact: true }).fill('empresa');
  await page.getByLabel('E-mail', { exact: true }).fill('vendedor@example.test');
  await page.getByLabel('Senha', { exact: true }).fill('test-password');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
}

/** Navegação da revenda; o menu lateral é o mesmo nos três tamanhos de tela. */
async function irPara(page, nome) {
  await page.getByRole('navigation', { name: 'Navegação da revenda' }).getByRole('button', { name: nome, exact: true }).click();
  await expect(page.getByRole('main')).toBeVisible();
}

async function semTransbordo(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
}

test('empresa somente REVENDA opera estoque, leads, propostas e vendas com identidade própria', async ({ page, context }) => {
  const state = await fixture(context, ['REVENDA']);
  const erros = []; page.on('pageerror', e => erros.push(e.message));
  await entrar(page);

  await expect(page.getByRole('heading', { name: 'Visão geral da revenda' })).toBeVisible();
  // Indicadores vêm de uma consulta agregada só; a tela não soma estoque no navegador.
  await expect(page.getByText('R$ 45.700,00')).toBeVisible();
  await expect(page.getByText('12.5')).toBeVisible();
  await semTransbordo(page);

  // O branding da empresa acompanha as telas novas: nada de nome de produto fixo.
  await expect(page.getByText('Empresa de teste').first()).toBeVisible();
  await expect(page.getByText('Garagem SaaS')).toHaveCount(0);

  await irPara(page, 'Estoque');
  await expect(page.getByRole('heading', { name: 'Estoque', level: 1 })).toBeVisible();
  await expect(page.getByText('ABC1D23').first()).toBeVisible();
  await semTransbordo(page);

  await irPara(page, 'Leads');
  await expect(page.getByRole('heading', { name: 'Leads', level: 1 })).toBeVisible();
  await expect(page.getByText('Compradora Ana').first()).toBeVisible();

  await irPara(page, 'Propostas');
  await expect(page.getByRole('heading', { name: 'Propostas', level: 1 })).toBeVisible();

  await irPara(page, 'Vendas');
  await expect(page.getByRole('heading', { name: 'Vendas', level: 1 })).toBeVisible();

  await irPara(page, 'Avaliações');
  await expect(page.getByRole('heading', { name: 'Avaliações', level: 1 })).toBeVisible();
  await semTransbordo(page);

  // Nada de oficina aparece para quem não contratou o módulo.
  const menu = page.getByRole('navigation', { name: 'Navegação da revenda' });
  await expect(menu.getByRole('button', { name: 'Ordens de Serviço' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Ir para oficina' })).toHaveCount(0);
  expect(state.escritas).toEqual([]);
  expect(erros).toEqual([]);
});

test('empresa somente OFICINA não recebe menu nem tela de revenda', async ({ page, context }) => {
  await fixture(context, ['OFICINA']);
  await entrar(page);
  await expect(page.getByRole('heading', { name: 'Um dia bem organizado.' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Navegação da revenda' })).toHaveCount(0);
  for (const nome of ['Estoque', 'Leads', 'Propostas', 'Vendas', 'Avaliações'])
    await expect(page.getByRole('button', { name: nome, exact: true })).toHaveCount(0);
});

test('empresa híbrida transita entre oficina e revenda na mesma sessão', async ({ page, context }) => {
  await fixture(context, ['OFICINA', 'REVENDA']);
  await entrar(page);
  await expect(page.getByRole('heading', { name: 'Um dia bem organizado.' })).toBeVisible();

  await page.getByRole('button', { name: 'Ir para revenda', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Visão geral da revenda' })).toBeVisible();

  await page.getByRole('button', { name: 'Ir para oficina', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Um dia bem organizado.' })).toBeVisible();
  await semTransbordo(page);
});

test('estoque vazio e falha de carregamento são informados sem quebrar a tela', async ({ page, context }) => {
  const state = estado(); state.estoque = [];
  await fixture(context, ['REVENDA'], state);
  await entrar(page);
  await irPara(page, 'Estoque');
  await expect(page.getByText('Nenhum registro encontrado.')).toBeVisible();

  // A partir daqui a API falha: a tela precisa dizer isso e oferecer nova tentativa.
  await page.route('**/api/v1/revenda/estoque**', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ detail: 'Serviço indisponível.', code: 'SERVICE_UNAVAILABLE' }) }));
  await page.getByRole('button', { name: 'Atualizar lista', exact: true }).click();
  await expect(page.getByRole('button', { name: /Tentar novamente/ })).toBeVisible();
});

test('tela de revenda é acessível', async ({ page, context }) => {
  await fixture(context, ['REVENDA']);
  await entrar(page);
  await irPara(page, 'Estoque');
  await expect(page.getByRole('heading', { name: 'Estoque', level: 1 })).toBeVisible();
  const resultado = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(resultado.violations.map(v => ({ id: v.id, nodes: v.nodes.map(n => n.target) }))).toEqual([]);
});
