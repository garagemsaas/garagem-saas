#!/usr/bin/env bash
# Sobe o ambiente de demonstração e o publica por um túnel HTTPS temporário.
#
# Isto NÃO é produção: o Quick Tunnel da Cloudflare sorteia um endereço novo a cada execução e só
# responde enquanto esta máquina estiver ligada com o processo de pé. Serve para teste, homologação
# e apresentação — nada além disso.
#
# Nenhum segredo mora aqui. Eles ficam em .tools/prod/producao.env, que o .gitignore cobre.
#
#   bash scripts/demo/publicar.sh
#
# Ao final o script imprime a URL pública e a grava em .tools/prod/api-url.txt. Se ela tiver mudado
# desde a última vez, o script atualiza a variável na Vercel e refaz o deploy de produção sozinho.
set -euo pipefail

raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$raiz"

env_file=".tools/prod/producao.env"
override="compose.demo.yml"
projeto="garagem-prod-val"
porta_api="8090"
frontend="https://plataforma-automotiva.vercel.app"

[ -f "$env_file" ] || { echo "Faltando $env_file (segredos do ambiente de demonstração)." >&2; exit 1; }

cloudflared="$(command -v cloudflared || echo '/c/Program Files (x86)/cloudflared/cloudflared.exe')"
[ -x "$cloudflared" ] || { echo "cloudflared não encontrado. Instale com: winget install Cloudflare.cloudflared" >&2; exit 1; }

echo "==> 1/5 subindo Postgres, MinIO e API"
docker compose -p "$projeto" --env-file "$env_file" -f compose.yml -f "$override" up -d

echo "==> 2/5 aguardando a API ficar saudável"
for _ in $(seq 1 40); do
  estado="$(docker inspect -f '{{.State.Health.Status}}' "${projeto}-api-1" 2>/dev/null || echo indisponivel)"
  [ "$estado" = "healthy" ] && break
  sleep 5
done
[ "${estado:-}" = "healthy" ] || { echo "A API não ficou saudável. Veja: docker logs ${projeto}-api-1" >&2; exit 1; }
echo "    API saudável em http://127.0.0.1:${porta_api}"

echo "==> 3/5 abrindo o túnel HTTPS (somente a porta da API)"
# `pkill` não alcança processos nativos do Windows: sem isto sobram túneis antigos e a URL lida do
# log pode pertencer a um deles, apontando para lugar nenhum.
taskkill //F //IM cloudflared.exe >/dev/null 2>&1 || true
sleep 2
: > .tools/prod/tunnel.log
nohup "$cloudflared" tunnel --url "http://127.0.0.1:${porta_api}" --no-autoupdate >> .tools/prod/tunnel.log 2>&1 &
for _ in $(seq 1 30); do
  url="$(grep -ao 'https://[a-z0-9-]*\.trycloudflare\.com' .tools/prod/tunnel.log | head -1 || true)"
  [ -n "$url" ] && break
  sleep 2
done
[ -n "${url:-}" ] || { echo "O túnel não publicou uma URL. Veja .tools/prod/tunnel.log" >&2; exit 1; }
echo "    $url"

echo "==> 4/5 conferindo a saúde pela internet"
# O túnel leva alguns segundos para propagar, daí a repetição. `--ssl-no-revoke` existe porque o
# schannel desta máquina não consegue consultar revogação de certificado e aborta a conexão; o
# `--doh-url` porque o resolvedor da rede local demora a enxergar subdomínios novos de
# trycloudflare.com e devolve "domínio inexistente" para um host que o resto do mundo já resolve.
for _ in $(seq 1 15); do
  saude="$(curl -s --ssl-no-revoke --doh-url https://cloudflare-dns.com/dns-query --max-time 30 "$url/actuator/health" || true)"
  case "$saude" in *'"status":"UP"'*) break ;; esac
  sleep 4
done
case "${saude:-}" in
  *'"status":"UP"'*) echo "    health UP" ;;
  *) echo "A API pública não respondeu UP: ${saude:-<vazio>}" >&2; exit 1 ;;
esac

echo "==> 5/5 apontando o frontend para esta URL"
anterior="$(cat .tools/prod/api-url.txt 2>/dev/null || true)"
if [ "$url" = "$anterior" ]; then
  echo "    a URL não mudou; nada a redeployar"
else
  ( cd frontend
    for ambiente in production preview; do
      npx --yes vercel env rm VITE_API_BASE_URL "$ambiente" --yes >/dev/null 2>&1 || true
      printf '%s' "$url" | npx --yes vercel env add VITE_API_BASE_URL "$ambiente" >/dev/null
    done
    npx --yes vercel --prod >/dev/null )
  printf '%s' "$url" > .tools/prod/api-url.txt
  echo "    Vercel atualizada e redeployada"
fi

echo
echo "Frontend: $frontend"
echo "API:      $url"
echo "Para encerrar: taskkill //F //IM cloudflared.exe && docker compose -p $projeto down"
