package br.com.garagem.veiculo.port;

import br.com.garagem.tenancy.TenantContext;
import br.com.garagem.veiculo.repository.VeiculoRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VeiculoAdapter implements VeiculoPort {
  private final VeiculoRepository veiculos;
  private final br.com.garagem.cliente.port.ClientePort clientes;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;
  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

  public VeiculoAdapter(
      VeiculoRepository veiculos,
      br.com.garagem.cliente.port.ClientePort clientes,
      org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.veiculos = veiculos;
    this.clientes = clientes;
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public void atualizarQuilometragem(UUID veiculoId, long km) {
    var v =
        veiculos
            .findByIdAndOficinaId(veiculoId, TenantContext.current())
            .orElseThrow(br.com.garagem.shared.error.ApiException::missing);
    if (km > v.km) v.km = km;
  }

  @Override
  public Optional<DadosVeiculo> buscar(UUID veiculoId) {
    return veiculos
        .findByIdAndOficinaId(veiculoId, TenantContext.current())
        .map(
            v ->
                new DadosVeiculo(
                    v.id, v.clienteId, v.placa, v.marca, v.modelo, v.km, v.propriedade));
  }

  @Override
  @Transactional
  public DadosVeiculo bloquear(UUID id) {
    var v =
        veiculos
            .lock(id, TenantContext.current())
            .orElseThrow(br.com.garagem.shared.error.ApiException::missing);
    return new DadosVeiculo(v.id, v.clienteId, v.placa, v.marca, v.modelo, v.km, v.propriedade);
  }

  @Override
  @Transactional
  public void adquirir(UUID id, UUID clienteEsperado, String motivo) {
    var v =
        veiculos
            .lock(id, TenantContext.current())
            .orElseThrow(br.com.garagem.shared.error.ApiException::missing);
    if ("EMPRESA".equals(v.propriedade) || !java.util.Objects.equals(v.clienteId, clienteEsperado))
      throw br.com.garagem.shared.error.ApiException.conflict(
          "A propriedade do veículo mudou ou já pertence à empresa.");
    auditar(motivo);
    v.propriedade = "EMPRESA";
    v.clienteId = null;
    entityManager.flush();
  }

  @Override
  @Transactional
  public void transferirAoCliente(UUID id, UUID clienteId, String motivo) {
    if (!clientes.existe(clienteId)) throw br.com.garagem.shared.error.ApiException.missing();
    var v =
        veiculos
            .lock(id, TenantContext.current())
            .orElseThrow(br.com.garagem.shared.error.ApiException::missing);
    if (!"EMPRESA".equals(v.propriedade))
      throw br.com.garagem.shared.error.ApiException.conflict("O veículo não pertence à empresa.");
    auditar(motivo);
    v.propriedade = "CLIENTE";
    v.clienteId = clienteId;
    entityManager.flush();
  }

  private void auditar(String motivo) {
    jdbc.queryForObject(
        "select set_config('app.autor_propriedade',?,true)",
        String.class,
        br.com.garagem.shared.seguranca.UsuarioAutenticado.id().toString());
    jdbc.queryForObject("select set_config('app.motivo_propriedade',?,true)", String.class, motivo);
  }
}
