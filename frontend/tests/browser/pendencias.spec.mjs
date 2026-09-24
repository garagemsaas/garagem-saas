import { test, expect } from '@playwright/test';
import { branding } from './company-fixture.mjs';

/**
 * As três pendências da simplificação: chamar no WhatsApp com confirmação, cadastrar cliente e
 * veículo sem sair da abertura do serviço, e Retornos existindo para quem só tem revenda.
 */
const sessao = { accessToken: 'test-access', refreshToken: 'test-refresh', oficinaId: 'e', usuarioId: 'u', nome: 'Ana', papel: 'OWNER', expiresIn: 900 };
const pagina = itens => ({ itens, pagina: 0, tamanho: 10, total: itens.length, totalPaginas: 1 });

async function fixture(context, modulos, estado) {
  await context.route('**/api/v1/**', async route => {
    const req = route.request();
    const caminho = new URL(req.url()).pathname.replace('/api/v1', '');
    const send = (d, s = 200) => route.fulfill({ status: s, contentType: 'application/json', body: JSON.stringify(d) });
    if (caminho === '/auth/login') return send(sessao);
    if (caminho === '/auth/logout') return route.fulfill({ status: 204 });
    if (caminho === '/auth/refresh') return send({ detail: 'Sessão expirada.' }, 401);
    if (caminho === '/empresa') return send({ branding, modulos, status: 'ATIVA', slug: 'e' });
    if (req.method() === 'POST' || req.method() === 'PUT') {
      const corpo = req.postDataJSON?.() ?? {};
      estado.escritas.push({ caminho, corpo });
      if (caminho === '/clientes') { const c = { id: 'cli-novo', nome: corpo.nome, telefone: corpo.telefone, email: null, revisao: 0 }; estado.clientes.push(c); return send(c, 201); }
      if (caminho === '/veiculos') { const v = { id: 'vei-novo', ...corpo, revisao: 0 }; estado.veiculos.push(v); return send(v, 201); }
      if (caminho === '/ordens-servico') return send({ id: 'os-1', numero: 1, ...corpo, status: 'RECEBIDO', revisao: 0, criadoEm: new Date().toISOString() }, 201);
      return send({ id: 'x', revisao: 1 }, 201);
    }
    if (caminho === '/retornos') return send(pagina(estado.retornos));
    if (caminho === '/dinheiro-esquecido/resumo') return send({ oportunidadesAbertas: 0, valorPotencialConhecido: 0, valorRecuperado: 0, quantidadeRecuperada: 0 });
    if (caminho.startsWith('/revenda/dashboard')) return send({ emAvaliacao: 0, emPreparacao: 0, disponiveis: 0, reservados: 0, vendidos: 0, valorAquisicao: 0, custosPreparacao: 0, valorAnunciado: 0, margemPotencial: 0, mediaDiasEstoque: 0, leadsAbertos: 0, propostasAbertas: 0, vendasPeriodo: 0, valorVendido: 0, margemRealizada: 0 });
    if (caminho === '/clientes') return send(pagina(estado.clientes));
    if (caminho === '/veiculos') return send(pagina(estado.veiculos));
    if (caminho === '/usuarios') return send(pagina([{ id: 'u', nome: 'Ana', email: 'a@t.local', papel: 'OWNER', ativo: true }]));
    if (caminho === '/dashboard') return send({ emAndamento: 0, prontas: 0, porStatus: {}, orcamentosAguardandoDecisao: { total: 0, quantidade: 0 } });
    return send(pagina([]));
  });
}

function estadoInicial() {
  return {
    escritas: [], clientes: [], veiculos: [],
    retornos: [{ tipo: 'INTERESSADO_SEM_RETORNO', id: 'r1', cliente: 'Marina Alves', telefone: '5511988887777', veiculo: 'Fiat Argo ABC1D23', motivo: 'Sem contato desde a abertura', responsavel: 'Ana', responsavelId: 'u', clienteId: 'c', prioridade: 'NORMAL', status: 'PENDENTE', revisao: 0, agendadoEm: '2026-09-01T12:00:00Z' }],
  };
}

async function entrar(page) {
  await page.goto('/');
  await page.getByLabel('Empresa', { exact: true }).fill('e');
  await page.getByLabel('E-mail', { exact: true }).fill('a@t.local');
  await page.getByLabel('Senha', { exact: true }).fill('test-password');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
}


/**
 * Em telas pequenas a navegação vive dentro do menu, e logo após entrar a tela ainda é o estado de
 * carregamento — sem menu e sem navegação. Esperar a casca aparecer resolve nos três tamanhos.
 */
async function irPara(page, nome) {
  const menu = page.getByRole('button', { name: 'Abrir menu', exact: true });
  const navegacao = page.getByRole('navigation', { name: /Navegação (principal|da revenda)/ });
  await expect(menu.or(navegacao.first())).toBeVisible();
  if (await menu.isVisible()) await menu.click();
  await navegacao.filter({ visible: true }).getByRole('button', { name: nome, exact: true }).click();
}

test('Retornos existe para empresa somente REVENDA e o WhatsApp confirma antes de abrir', async ({ page, context }) => {
  const estado = estadoInicial();
  await fixture(context, ['REVENDA'], estado);
  await entrar(page);

  await irPara(page, 'Retornos');
  await expect(page.getByRole('heading', { name: 'Retornos', level: 1 })).toBeVisible();
  await expect(page.getByText('Sem contato desde a abertura')).toBeVisible();
  await expect(page.getByText('Marina Alves')).toBeVisible();

  // Nada é aberto antes da confirmação: primeiro o telefone e o texto ficam à vista.
  await page.getByRole('button', { name: 'Chamar no WhatsApp' }).click();
  const confirmacao = page.getByRole('dialog', { name: 'Chamar no WhatsApp' });
  await expect(confirmacao.getByLabel('Telefone')).toHaveValue('5511988887777');
  const mensagem = confirmacao.getByLabel('Mensagem');
  await expect(mensagem).toHaveValue(/Empresa de teste/);
  await mensagem.fill('Oi Marina, ainda tem interesse no Argo?');

  const [aba] = await Promise.all([
    context.waitForEvent('page'),
    confirmacao.getByRole('button', { name: 'Abrir conversa' }).click(),
  ]);
  // O wa.me redireciona para o domínio do WhatsApp; o que precisa sobreviver é número e texto.
  // O redirecionamento reescreve espaços como '+', então normaliza-se antes de comparar.
  const destino = decodeURIComponent(aba.url()).replaceAll('+', ' ');
  expect(destino).toContain('5511988887777');
  expect(destino).toContain('Oi Marina, ainda tem interesse no Argo?');
  expect(destino).toMatch(/wa\.me|whatsapp\.com/);
  await aba.close();

  // Abrir a conversa não é enviar: nada foi gravado como contato.
  expect(estado.escritas).toEqual([]);
});

test('empresa somente OFICINA continua com Retornos e sem a tela da revenda', async ({ page, context }) => {
  const estado = estadoInicial();
  await fixture(context, ['OFICINA'], estado);
  await entrar(page);
  await irPara(page, 'Retornos');
  // Na oficina, Retornos abre como painel sobre a tela de trabalho, não como página à parte.
  await expect(page.getByRole('dialog', { name: 'Retornos' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Navegação da revenda' })).toHaveCount(0);
});

test('novo serviço permite cadastrar cliente e veículo sem sair da tela', async ({ page, context }) => {
  const estado = estadoInicial();
  await fixture(context, ['OFICINA'], estado);
  await entrar(page);

  await irPara(page, 'Serviços');
  await page.getByRole('button', { name: 'Abrir OS', exact: true }).click();

  // Sem veículo nenhum, a abertura do serviço já começa pelo cadastro em vez de mandar embora.
  await expect(page.getByLabel('Nome do cliente *')).toBeVisible();
  await page.getByLabel('Nome do cliente *').fill('Marina Alves');
  await page.getByLabel('Telefone *').fill('11988887777');
  await page.getByLabel('Placa *').fill('ABC1D23');
  await page.getByLabel('Marca *').fill('Fiat');
  await page.getByLabel('Modelo *').fill('Argo');
  await page.getByLabel('Ano *').fill('2021');
  await page.getByLabel('Quilometragem *').fill('30000');
  await page.getByLabel('Cor *').fill('Prata');
  await page.getByRole('button', { name: 'Cadastrar e continuar', exact: true }).click();

  // Cliente e veículo criados uma vez cada, e o serviço segue na mesma tela.
  await expect(page.getByLabel('Relato do cliente *')).toBeVisible();
  expect(estado.escritas.filter(e => e.caminho === '/clientes')).toHaveLength(1);
  expect(estado.escritas.filter(e => e.caminho === '/veiculos')).toHaveLength(1);
  expect(estado.escritas.find(e => e.caminho === '/veiculos').corpo.clienteId).toBe('cli-novo');
  // O veículo recém-criado já vem selecionado: quem cadastrou não precisa procurá-lo na lista.
  await expect(page.getByLabel('Veículo *', { exact: true })).toHaveValue('vei-novo');
});
