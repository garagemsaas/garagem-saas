import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * Site público da empresa. É a única página do produto feita para quem nunca vai entrar no sistema,
 * e o que se protege aqui é isso: ela se pinta com a identidade da empresa, não pede nada de
 * ninguém e não menciona o produto que a hospeda além da assinatura do rodapé.
 */
const site = {
  nome: 'Oficina do Bairro',
  frase: 'Mecânica de confiança desde 2004',
  sobre: 'Atendemos carros de passeio e utilitários.\n\nAgendamento por telefone ou WhatsApp.',
  servicos: ['Revisão', 'Troca de óleo', 'Freios', 'Suspensão'],
  endereco: 'Rua das Oficinas, 100 - São Paulo',
  horario: 'Segunda a sexta, 8h às 18h',
  telefone: '(11) 3333-4444',
  whatsapp: '5511999998888',
  instagram: 'oficina.do.bairro',
  corPrimaria: '#8a2b2b',
  corSecundaria: '#2b1616',
  logoId: null,
  capaId: null,
  revisao: 3,
};

async function publicar(page, corpo = site, status = 200) {
  await page.route('**/api/v1/site/**', route => route.fulfill({
    status, contentType: 'application/json', body: JSON.stringify(corpo),
  }));
}

async function semTransbordo(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true);
}

test('site da empresa apresenta contato e identidade sem pedir nada ao visitante', async ({ page }) => {
  const chamadas = [];
  page.on('request', r => { if (r.url().includes('/api/v1/')) chamadas.push(new URL(r.url()).pathname); });
  await publicar(page);
  await page.goto('/site/oficina-do-bairro');

  await expect(page.getByRole('heading', { name: 'Oficina do Bairro', level: 1 })).toBeVisible();
  await expect(page.getByText('Mecânica de confiança desde 2004')).toBeVisible();
  await expect(page).toHaveTitle('Oficina do Bairro');

  // A cor da empresa pinta a página: é o mesmo template para todo mundo.
  //
  // A leitura é no elemento que de fato desenha o site, não no <html>. A folha declara o padrão em
  // `.site`; enquanto a cor era gravada no documento, a própria regra do elemento a sobrescrevia e
  // todo cliente saía com a cor de exemplo — verde — com esta verificação passando assim mesmo.
  expect(await page.evaluate(() => getComputedStyle(document.querySelector('.site')).getPropertyValue('--empresa').trim()))
    .toBe('#8a2b2b');
  // E o texto sobre ela é escolhido pelo contraste, não pelo gosto: sobre vinho escuro, branco.
  expect(await page.evaluate(() => getComputedStyle(document.querySelector('.site')).getPropertyValue('--empresa-texto').trim()))
    .toBe('#ffffff');

  await expect(page.getByRole('heading', { name: 'Serviços' })).toBeVisible();
  for (const servico of site.servicos) await expect(page.getByText(servico, { exact: true })).toBeVisible();

  await expect(page.getByRole('heading', { name: 'Sobre' })).toBeVisible();
  await expect(page.getByText('Atendemos carros de passeio e utilitários.')).toBeVisible();

  await expect(page.getByText(site.endereco)).toBeVisible();
  await expect(page.getByText(site.horario)).toBeVisible();
  await expect(page.getByRole('link', { name: 'Ver no mapa' })).toHaveAttribute('href', /google\.com\/maps/);
  await expect(page.getByRole('link', { name: site.telefone })).toHaveAttribute('href', 'tel:1133334444');
  await expect(page.getByRole('link', { name: '@oficina.do.bairro' }))
    .toHaveAttribute('href', 'https://instagram.com/oficina.do.bairro');

  const whats = page.getByRole('link', { name: 'Falar no WhatsApp' });
  await expect(whats).toHaveAttribute('href', 'https://wa.me/5511999998888');

  await expect(page.getByText('Site desenvolvido por Plataforma Automotiva')).toBeVisible();

  // Página informativa: nada de formulário, cadastro ou função por vir.
  await expect(page.locator('form')).toHaveCount(0);
  await expect(page.locator('input')).toHaveCount(0);
  await expect(page.getByText('Em breve', { exact: false })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Entrar' })).toHaveCount(0);

  // Só a leitura do próprio site: o visitante não dispara nada de operação. Conta-se o destino,
  // não a quantidade — em desenvolvimento o StrictMode executa o efeito duas vezes de propósito.
  expect(chamadas.length).toBeGreaterThan(0);
  expect([...new Set(chamadas)]).toEqual(['/api/v1/site/oficina-do-bairro']);
  await semTransbordo(page);
});

test('site respeita quem não publicou e continua acessível', async ({ page }) => {
  await publicar(page, { detail: 'Recurso não encontrado.' }, 404);
  await page.goto('/site/empresa-fechada');
  await expect(page.getByRole('heading', { name: 'Página indisponível' })).toBeVisible();
  await semTransbordo(page);

  await publicar(page);
  await page.goto('/site/oficina-do-bairro');
  await expect(page.getByRole('heading', { name: 'Oficina do Bairro', level: 1 })).toBeVisible();
  const resultado = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(resultado.violations.map(v => ({ id: v.id, nodes: v.nodes.map(n => n.target) }))).toEqual([]);
});

test('site omite seções que a empresa não preencheu', async ({ page }) => {
  await publicar(page, { ...site, servicos: [], sobre: null, instagram: null, whatsapp: null });
  await page.goto('/site/oficina-do-bairro');
  await expect(page.getByRole('heading', { name: 'Oficina do Bairro', level: 1 })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Serviços' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Sobre' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Falar no WhatsApp' })).toHaveCount(0);
  // Contato sobrevive com o que houver: o endereço e o horário seguem visíveis.
  await expect(page.getByRole('heading', { name: 'Contato' })).toBeVisible();
  await expect(page.getByText(site.endereco)).toBeVisible();
  await semTransbordo(page);
});

/**
 * O critério que realmente importa para o modelo comercial: duas empresas completamente diferentes
 * saem do mesmo código. Se este teste exigisse um componente, uma pasta ou um CSS por cliente, cada
 * venda custaria um commit e um deploy — e o produto deixaria de ser configurável para virar um
 * projeto sob encomenda por oficina.
 */
test('duas empresas diferentes saem do mesmo template, sem uma linha de código por cliente', async ({ page }) => {
  const empresaA = {
    ...site, nome: 'Oficina do Bairro', frase: 'Mecânica de confiança desde 2004',
    servicos: ['Revisão', 'Freios'], corPrimaria: '#8a2b2b', corSecundaria: '#2b1616',
    endereco: 'Rua das Oficinas, 100 - São Paulo', instagram: 'oficina.do.bairro',
  };
  const empresaB = {
    ...site, nome: 'Garagem Litoral', frase: 'Seminovos revisados com garantia',
    sobre: 'Loja de seminovos na orla.', servicos: ['Compra de usados', 'Financiamento'],
    endereco: 'Avenida Beira-Mar, 900 - Santos', horario: 'Todos os dias, 9h às 19h',
    telefone: '(13) 2222-1111', whatsapp: '5513911112222', instagram: 'garagem.litoral',
    corPrimaria: '#f2c94c', corSecundaria: '#8a6d12',
  };

  await publicar(page, empresaA);
  await page.goto('/site/oficina-do-bairro');
  await expect(page.getByRole('heading', { name: 'Oficina do Bairro', level: 1 })).toBeVisible();
  await expect(page.getByText('Mecânica de confiança desde 2004')).toBeVisible();
  const identidadeA = await page.evaluate(() => {
    const s = getComputedStyle(document.querySelector('.site'));
    return { cor: s.getPropertyValue('--empresa').trim(), texto: s.getPropertyValue('--empresa-texto').trim() };
  });
  expect(identidadeA).toEqual({ cor: '#8a2b2b', texto: '#ffffff' });

  await page.unrouteAll();
  await publicar(page, empresaB);
  await page.goto('/site/garagem-litoral');
  await expect(page.getByRole('heading', { name: 'Garagem Litoral', level: 1 })).toBeVisible();
  await expect(page.getByText('Seminovos revisados com garantia')).toBeVisible();
  await expect(page.getByText('Compra de usados', { exact: true })).toBeVisible();
  await expect(page.getByText('Avenida Beira-Mar, 900 - Santos')).toBeVisible();

  // Nada da empresa A sobrevive na página da empresa B.
  await expect(page.getByText('Oficina do Bairro')).toHaveCount(0);
  await expect(page.getByText('Mecânica de confiança desde 2004')).toHaveCount(0);
  await expect(page.getByText('Revisão', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('link', { name: '@oficina.do.bairro' })).toHaveCount(0);

  // Sobre amarelo claro o texto vira escuro sozinho: o contraste não depende de quem escolhe a cor.
  const identidadeB = await page.evaluate(() => {
    const s = getComputedStyle(document.querySelector('.site'));
    return { cor: s.getPropertyValue('--empresa').trim(), texto: s.getPropertyValue('--empresa-texto').trim() };
  });
  expect(identidadeB).toEqual({ cor: '#f2c94c', texto: '#151a17' });

  await semTransbordo(page);
  const relatorio = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze();
  expect(relatorio.violations.map(v => ({ id: v.id, nodes: v.nodes.map(n => n.target) }))).toEqual([]);
});
