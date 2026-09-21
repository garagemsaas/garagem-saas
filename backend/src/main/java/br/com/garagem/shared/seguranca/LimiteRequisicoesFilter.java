package br.com.garagem.shared.seguranca;

import br.com.garagem.shared.error.ErrorCodes;
import br.com.garagem.shared.error.ProblemJson;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Teto de requisições nas superfícies anônimas. Protege login e refresh contra força bruta e enxame
 * de credenciais, e limita o custo que um anônimo impõe ao servidor.
 *
 * <p>O login é o ponto mais caro da aplicação: cada tentativa gasta um BCrypt de custo 12, cerca de
 * 400 ms de CPU. Sem teto, algumas centenas de requisições simultâneas saturam o processador — a
 * proteção contra descoberta de senha é, ao mesmo tempo, a proteção contra negação de serviço.
 *
 * <p>Fica logo após o filtro de correlação e antes da cadeia de segurança: barrar cedo é o ponto de
 * gastar menos com quem está abusando.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class LimiteRequisicoesFilter extends OncePerRequestFilter {
  /** Uma regra por superfície: tetos diferentes para custos e riscos diferentes. */
  private record Regra(String nome, String prefixo, String metodo, LimiteRequisicoes limite) {
    boolean casa(HttpServletRequest req) {
      return req.getRequestURI().startsWith(prefixo)
          && (metodo == null || metodo.equalsIgnoreCase(req.getMethod()));
    }
  }

  private final boolean habilitado;
  private final List<Regra> regras;
  private final LimiteRequisicoes login;
  private final boolean confiarProxy;
  private long ultimaLimpeza = System.nanoTime();

  public LimiteRequisicoesFilter(
      @Value("${app.seguranca.rate-limit.habilitado:true}") boolean habilitado,
      @Value("${app.seguranca.rate-limit.login:10}") int tetoLogin,
      @Value("${app.seguranca.rate-limit.publico:60}") int tetoPublico,
      @Value("${app.seguranca.rate-limit.webhook:120}") int tetoWebhook,
      @Value("${app.seguranca.rate-limit.janela:PT1M}") Duration janela,
      @Value("${app.seguranca.confiar-proxy:false}") boolean confiarProxy) {
    this.habilitado = habilitado;
    this.confiarProxy = confiarProxy;
    this.login = new LimiteRequisicoes(tetoLogin, janela);
    this.regras =
        List.of(
            new Regra("login", "/api/v1/auth/login", "POST", login),
            new Regra(
                "refresh",
                "/api/v1/auth/refresh",
                "POST",
                new LimiteRequisicoes(tetoLogin, janela)),
            new Regra(
                "publico", "/api/v1/publico/", null, new LimiteRequisicoes(tetoPublico, janela)),
            // O site da empresa é anônimo como o acompanhamento público, e cai no mesmo teto.
            new Regra("site", "/api/v1/site/", null, new LimiteRequisicoes(tetoPublico, janela)),
            new Regra(
                "webhook",
                "/api/v1/webhooks/",
                "POST",
                new LimiteRequisicoes(tetoWebhook, janela)));
    LoggerFactory.getLogger(LimiteRequisicoesFilter.class)
        .atInfo()
        .addKeyValue("rate_limit_habilitado", habilitado)
        .addKeyValue("rate_limit_login", tetoLogin)
        .addKeyValue("rate_limit_janela_s", janela.toSeconds())
        .addKeyValue("confiar_proxy", confiarProxy)
        .log("rate_limit_configurado");
  }

  /** Zera a contagem de login de uma origem que acabou de autenticar com sucesso. */
  public void sucessoDeLogin(HttpServletRequest req) {
    login.liberar("login|" + origem(req));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (!habilitado) {
      chain.doFilter(req, res);
      return;
    }
    limparPeriodicamente();
    for (var regra : regras) {
      if (!regra.casa(req)) continue;
      String chave = regra.nome() + "|" + origem(req);
      if (!regra.limite().permitir(chave)) {
        recusar(res, regra, chave);
        return;
      }
      break;
    }
    chain.doFilter(req, res);
  }

  private void recusar(HttpServletResponse res, Regra regra, String chave) throws IOException {
    long espera = regra.limite().esperaSegundos(chave);
    MDC.put("rate_limit", regra.nome());
    // Sem a origem no log: a chave contém o IP do cliente, que é dado pessoal, e o nome da regra
    // já basta para diagnosticar. O request_id correlaciona com o registro de acesso.
    LoggerFactory.getLogger(LimiteRequisicoesFilter.class)
        .atWarn()
        .addKeyValue("rate_limit_regra", regra.nome())
        .log("rate_limit_excedido");
    res.setHeader("Retry-After", String.valueOf(Math.max(1, espera)));
    ProblemJson.write(
        res,
        429,
        ErrorCodes.RATE_LIMITED,
        "Muitas tentativas. Aguarde " + Math.max(1, espera) + " segundo(s) e tente novamente.");
  }

  /**
   * Origem do cliente. Por padrão usa o endereço da conexão, que não é forjável.
   *
   * <p>{@code confiar-proxy} só deve ser ligado quando a aplicação estiver atrás de um proxy que
   * sobrescreve X-Forwarded-For: se for ligado com a porta exposta diretamente, qualquer um forja o
   * cabeçalho e escapa do teto. Desligado atrás de um proxy, o efeito oposto — todos os clientes
   * compartilham o IP do proxy e um único abusador barra a oficina inteira. Ver docs/staging.md.
   */
  String origem(HttpServletRequest req) {
    if (confiarProxy) {
      String encaminhado = req.getHeader("X-Forwarded-For");
      if (encaminhado != null && !encaminhado.isBlank()) {
        String primeiro = encaminhado.split(",")[0].trim();
        if (!primeiro.isEmpty()) return primeiro;
      }
    }
    String remoto = req.getRemoteAddr();
    return remoto == null ? "desconhecido" : remoto;
  }

  /** Varredura barata e oportunista, sem agendador: o projeto não tem um. */
  private void limparPeriodicamente() {
    long agora = System.nanoTime();
    if (agora - ultimaLimpeza < Duration.ofMinutes(5).toNanos()) return;
    ultimaLimpeza = agora;
    for (var regra : regras) regra.limite().limpar();
  }
}
