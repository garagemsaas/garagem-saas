package br.com.garagem.assinatura.repository;

import br.com.garagem.assinatura.domain.WebhookPagamento;
import java.util.*;
import org.springframework.data.repository.Repository;

public interface WebhookPagamentoRepository extends Repository<WebhookPagamento, UUID> {
  Optional<WebhookPagamento> findByProvedorAndProviderEventId(String provedor, String eventId);

  WebhookPagamento save(WebhookPagamento webhook);
}
