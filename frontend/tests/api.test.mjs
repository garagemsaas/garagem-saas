import test, { afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { api, allPages, listPage, loadData, currentSession, setApiSession, photoBlob, ApiError } from '../src/api.ts';

const originalFetch = globalThis.fetch;
const originalWindow = globalThis.window;
const fakeSession = { accessToken: 'access-test', refreshToken: 'refresh-test', oficinaId: 'oficina-test', usuarioId: 'user-test', nome: 'Teste', papel: 'OWNER', expiresIn: 900 };
const response = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
test('listagem solicita somente a página e codifica busca sem criar parâmetros', async () => {
  let calls = 0;
  globalThis.fetch = async path => {
    calls++;
    const url = new URL(path, 'http://localhost');
    assert.equal(url.searchParams.get('pagina'), '2');
    assert.equal(url.searchParams.get('tamanho'), '10');
    assert.equal(url.searchParams.get('busca'), 'Ana & João');
    return response({ itens: [{ id: 'c21' }], total: 21, pagina: 2, tamanho: 10 });
  };
  const result = await loadData('clients', 2, 'Ana & João');
  assert.equal(calls, 1); assert.equal(result.total, 21); assert.equal(result.clientes[0].id, 'c21');
});

test('página de veículos resolve proprietário fora da página sem buscar a base inteira', async () => {
  const calls = [];
  globalThis.fetch = async path => {
    calls.push(path);
    if (path.includes('/veiculos?')) return response({ itens: [{ id: 'v', clienteId: 'c' }], total: 34 });
    assert.equal(path, '/api/v1/clientes/c');
    return response({ id: 'c', nome: 'Cliente relacionado' });
  };
  const result = await loadData('vehicles');
  assert.equal(calls.length, 2); assert.equal(result.clientes[0].id, 'c'); assert.equal(result.total, 34);
});

test('busca preserva filtro de status e omite busca vazia', async () => {
  globalThis.fetch = async path => {
    const params = new URL(path, 'http://localhost').searchParams;
    assert.equal(params.get('status'), 'PRONTO'); assert.equal(params.has('busca'), false);
    return response({ itens: [], total: 0 });
  };
  await listPage('/ordens-servico?status=PRONTO');
});

test('JSON de sucesso inválido retorna erro compreensível', async () => {
  globalThis.fetch = async () => new Response('<html>Proxy</html>');
  await assert.rejects(api('/clientes'), e => e instanceof ApiError && e.message.includes('resposta inválida'));
});

test('401 atrasado reutiliza token já renovado sem rotacionar novamente', async () => {
  setApiSession(fakeSession);
  let finishSlow;
  let refreshes = 0;
  globalThis.fetch = async (path, options) => {
    if (path.endsWith('/auth/refresh')) { refreshes++; return response({ ...fakeSession, accessToken: 'new-token' }); }
    if (options.headers.Authorization === 'Bearer new-token') return response({ ok: true });
    if (path.endsWith('/slow')) return new Promise(resolve => { finishSlow = resolve; });
    return response({}, 401);
  };
  const slow = api('/slow');
  await api('/fast');
  finishSlow(response({}, 401));
  await slow;
  assert.equal(refreshes, 1);
});
afterEach(() => { globalThis.fetch = originalFetch; globalThis.window = originalWindow; setApiSession(null); });

test('leituras concorrentes renovam um refresh uma única vez e repetem com o novo JWT', async () => {
  setApiSession(fakeSession);
  let refreshes = 0;
  globalThis.fetch = async (path, options) => {
    if (path.endsWith('/auth/refresh')) {
      refreshes++;
      assert.deepEqual(JSON.parse(options.body), { oficinaId: fakeSession.oficinaId, refreshToken: fakeSession.refreshToken });
      await new Promise(resolve => setTimeout(resolve, 10));
      return response({ ...fakeSession, accessToken: 'renewed-test', refreshToken: 'rotated-test' });
    }
    if (options.headers.Authorization === 'Bearer access-test') return response({ detail: 'expirado' }, 401);
    assert.equal(options.headers.Authorization, 'Bearer renewed-test');
    return response({ itens: [] });
  };
  await Promise.all([api('/clientes'), api('/veiculos'), api('/usuarios')]);
  assert.equal(refreshes, 1);
  assert.equal(currentSession().refreshToken, 'rotated-test');
});

test('resposta antiga não entrega dados depois de sair ou trocar oficina', async () => {
  setApiSession(fakeSession);
  let finish;
  globalThis.fetch = () => new Promise(resolve => { finish = resolve; });
  const pending = api('/clientes');
  setApiSession({ ...fakeSession, oficinaId: 'outra-oficina' });
  finish(response({ itens: [{ nome: 'Não entregar' }] }));
  await assert.rejects(pending, e => e instanceof ApiError && e.status === 401);
});

test('refresh recusado limpa sessão e avisa a interface', async () => {
  setApiSession(fakeSession);
  const events = [];
  globalThis.window = { dispatchEvent: event => events.push(event.type) };
  globalThis.fetch = async () => response({ detail: 'expirado' }, 401);
  await assert.rejects(api('/clientes'), e => e.status === 401);
  assert.equal(currentSession(), null);
  assert.deepEqual(events, ['session-expired']);
});

test('percorre páginas reais sem truncar a oficina em 100 registros', async () => {
  let calls = 0;
  globalThis.fetch = async path => {
    calls++;
    const page = Number(new URL(path, 'http://localhost').searchParams.get('pagina'));
    return response({ itens: Array.from({ length: page === 0 ? 100 : 1 }, (_, i) => ({ id: page * 100 + i })), total: 101, pagina: page, tamanho: 100 });
  };
  const rows = await allPages('/clientes');
  assert.equal(rows.length, 101); assert.equal(rows.at(-1).id, 100); assert.equal(calls, 2);
});

test('download de foto usa Bearer, no-store e conteúdo binário', async () => {
  setApiSession(fakeSession);
  globalThis.fetch = async (path, options) => {
    assert.equal(path, '/api/v1/ordens-servico/os/fotos/foto/conteudo');
    assert.equal(options.headers.Authorization, 'Bearer access-test'); assert.equal(options.cache, 'no-store');
    return new Response(new Uint8Array([1, 2, 3]), { headers: { 'Content-Type': 'image/png' } });
  };
  const blob = await photoBlob('/ordens-servico/os/fotos/foto/conteudo');
  assert.equal(blob.type, 'image/png'); assert.equal(blob.size, 3);
});

test('requisições públicas não recebem JWT e conflitos preservam detail', async () => {
  setApiSession(fakeSession);
  globalThis.fetch = async (_path, options) => {
    assert.equal(options.headers.Authorization, undefined);
    return response({ detail: 'Versão substituída.' }, 409);
  };
  await assert.rejects(api('/publico/token-test/decisao', 'POST', { versaoId: 'versao-test', aprovado: true }), e => e.status === 409 && e.message === 'Versão substituída.');
});

test('multipart preserva FormData e deixa o navegador definir boundary', async () => {
  const form = new FormData(); form.append('finalidade', 'ENTRADA');
  globalThis.fetch = async (_path, options) => {
    assert.equal(options.body, form); assert.equal(options.headers['Content-Type'], undefined);
    return response({ id: 'foto-test' }, 201);
  };
  assert.deepEqual(await api('/ordens-servico/os/fotos', 'POST', form), { id: 'foto-test' });
});

test('erros preservam código, campos e identificador para suporte', async () => {
  globalThis.fetch = async () => new Response(JSON.stringify({ code: 'VALIDATION_ERROR', detail: 'Confira os dados.', errors: [{ field: 'email', message: 'E-mail inválido.' }] }), { status: 400, headers: { 'X-Request-Id': 'request-test' } });
  await assert.rejects(api('/clientes', 'POST', {}), error => error.code === 'VALIDATION_ERROR' && error.requestId === 'request-test' && error.errors[0].field === 'email');
});

test('falha de rede mostra mensagem compreensível', async () => {
  globalThis.fetch = async () => { throw new TypeError('Failed to fetch'); };
  await assert.rejects(api('/clientes'), error => error.status === 0 && error.message.includes('conexão'));
});

test('erro sem JSON usa mensagem específica e logout aceita 204', async () => {
  globalThis.fetch = async () => new Response('Unavailable', { status: 503 });
  await assert.rejects(api('/clientes'), error => error.message.includes('temporariamente indisponível'));
  globalThis.fetch = async () => new Response(null, { status: 204 });
  assert.equal(await api('/auth/logout', 'POST', {}), undefined);
});

test('paginação preserva filtros existentes', async () => {
  globalThis.fetch = async path => {
    const params = new URL(path, 'http://localhost').searchParams;
    assert.equal(params.get('busca'), 'Ana'); assert.equal(params.get('pagina'), '0');
    return response({ itens: [], total: 0 });
  };
  assert.deepEqual(await allPages('/clientes?busca=Ana'), []);
});

test('refresh antigo não substitui a nova sessão', async () => {
  setApiSession(fakeSession);
  let finishRefresh;
  let started;
  const ready = new Promise(resolve => { started = resolve; });
  globalThis.fetch = async path => {
    if (path.endsWith('/auth/refresh')) { started(); return new Promise(resolve => { finishRefresh = resolve; }); }
    return response({}, 401);
  };
  const pending = api('/clientes');
  await ready;
  setApiSession({ ...fakeSession, oficinaId: 'nova-oficina' });
  finishRefresh(response({ ...fakeSession, accessToken: 'old-renewed' }));
  await assert.rejects(pending, error => error.status === 401);
  assert.equal(currentSession().oficinaId, 'nova-oficina');
});
