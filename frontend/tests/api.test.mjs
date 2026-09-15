import test, { afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { api, allPages, currentSession, setApiSession, photoBlob, ApiError } from '../src/api.ts';

const originalFetch = globalThis.fetch;
const originalWindow = globalThis.window;
const fakeSession = { accessToken: 'access-test', refreshToken: 'refresh-test', oficinaId: 'oficina-test', usuarioId: 'user-test', nome: 'Teste', papel: 'OWNER', expiresIn: 900 };
const response = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
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
