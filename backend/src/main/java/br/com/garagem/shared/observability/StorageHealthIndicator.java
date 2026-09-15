package br.com.garagem.shared.observability;

import br.com.garagem.ordemservico.foto.application.FotoStorage;
import org.springframework.boot.actuate.health.*;
import org.springframework.stereotype.Component;

/**
 * Health do armazenamento de fotos. Consulta apenas a existência do destino, então não lê objeto
 * nem revela nome de arquivo. Entra no agregado de {@code /actuator/health}: sem storage, a API não
 * consegue receber nem servir fotos.
 */
@Component("storage")
public class StorageHealthIndicator implements HealthIndicator {
  private final FotoStorage storage;

  public StorageHealthIndicator(FotoStorage storage) {
    this.storage = storage;
  }

  @Override
  public Health health() {
    try {
      return storage.disponivel() ? Health.up().build() : Health.down().build();
    } catch (RuntimeException e) {
      return Health.down().withDetail("motivo", e.getClass().getSimpleName()).build();
    }
  }
}
