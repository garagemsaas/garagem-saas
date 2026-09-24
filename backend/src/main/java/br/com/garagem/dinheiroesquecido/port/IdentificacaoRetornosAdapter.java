package br.com.garagem.dinheiroesquecido.port;

import br.com.garagem.dinheiroesquecido.application.IdentificacaoService;
import org.springframework.stereotype.Component;

@Component
public class IdentificacaoRetornosAdapter implements IdentificacaoRetornosPort {
  private final IdentificacaoService service;

  public IdentificacaoRetornosAdapter(IdentificacaoService service) {
    this.service = service;
  }

  @Override
  public void identificar() {
    service.identificar();
  }
}
