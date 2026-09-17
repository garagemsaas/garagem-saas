package br.com.garagem.assinatura.application.pagamento;

import br.com.garagem.assinatura.domain.Assinatura;
import br.com.garagem.assinatura.domain.Plano;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Provedor padrão enquanto nenhum gateway estiver contratado. Não inventa integração: a cobrança é
 * conduzida fora do sistema (boleto, PIX, maquininha) e confirmada pelo mesmo webhook assinado que
 * um gateway usaria, o que mantém um único caminho de ativação e de inadimplência.
 *
 * <p>Ao contratar um gateway, publique outro {@code @Component} de {@link PagamentoProvider} com
 * {@code @ConditionalOnProperty} no próprio nome e aponte {@code PAYMENT_PROVIDER} para ele: esta
 * classe sai de cena e nenhum outro arquivo muda.
 */
@Component
@ConditionalOnProperty(
    name = "app.pagamento.provedor",
    havingValue = "MANUAL",
    matchIfMissing = true)
public class ProvedorManual implements PagamentoProvider {
  public static final String NOME = "MANUAL";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final String segredo;

  public ProvedorManual(@Value("${app.pagamento.webhook-secret:}") String segredo) {
    this.segredo = segredo;
  }

  @Override
  public String nome() {
    return NOME;
  }

  @Override
  public String criarCliente(Assinatura a, String nomeOficina, String emailResponsavel) {
    // Sem gateway, o "cliente remoto" é a própria oficina: identificador estável e sem dado
    // pessoal.
    return "manual:" + a.oficinaId;
  }

  @Override
  public String criarAssinatura(Assinatura a, Plano plano) {
    return "manual:" + a.oficinaId + ":" + plano.codigo;
  }

  @Override
  public void cancelarAssinatura(Assinatura a, boolean imediato) {
    // O cancelamento é inteiramente local; não há chamada remota a fazer.
  }

  @Override
  public void reativarAssinatura(Assinatura a) {}

  @Override
  public void mudarPlano(Assinatura a, Plano destino) {}

  @Override
  public Optional<String> consultarAssinatura(Assinatura a) {
    return Optional.ofNullable(a.providerSubscriptionId);
  }

  /**
   * HMAC-SHA256 do corpo cru com o segredo do ambiente, comparado em tempo constante. Sem segredo
   * configurado nenhum webhook é aceito: falhar fechado evita que um ambiente mal configurado ative
   * assinaturas a partir de uma requisição anônima.
   */
  @Override
  public boolean assinaturaValida(String corpoCru, Map<String, String> cabecalhos) {
    if (segredo == null || segredo.isBlank()) return false;
    String recebida = cabecalhos.get("x-garagem-signature");
    if (recebida == null || recebida.isBlank()) return false;
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] esperada = mac.doFinal(corpoCru.getBytes(StandardCharsets.UTF_8));
      return MessageDigest.isEqual(esperada, hexParaBytes(recebida.trim()));
    } catch (java.security.GeneralSecurityException e) {
      return false;
    }
  }

  private static byte[] hexParaBytes(String hex) {
    if (hex.length() % 2 != 0) return new byte[0];
    byte[] saida = new byte[hex.length() / 2];
    for (int i = 0; i < saida.length; i++) {
      int alto = Character.digit(hex.charAt(i * 2), 16);
      int baixo = Character.digit(hex.charAt(i * 2 + 1), 16);
      if (alto < 0 || baixo < 0) return new byte[0];
      saida[i] = (byte) ((alto << 4) | baixo);
    }
    return saida;
  }

  @Override
  public EventoExterno lerEvento(String corpoCru) {
    try {
      var node = JSON.readTree(corpoCru);
      String id = texto(node, "id");
      String tipo = texto(node, "tipo");
      if (id == null || tipo == null) return null;
      return new EventoExterno(id, tipo, texto(node, "assinaturaExterna"));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      return null;
    }
  }

  private static String texto(com.fasterxml.jackson.databind.JsonNode node, String campo) {
    var valor = node.get(campo);
    return valor == null || !valor.isTextual() || valor.asText().isBlank() ? null : valor.asText();
  }
}
