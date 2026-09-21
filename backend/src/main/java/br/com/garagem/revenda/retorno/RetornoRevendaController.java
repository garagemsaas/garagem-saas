package br.com.garagem.revenda.retorno;

import br.com.garagem.oficina.ModuloEmpresa;
import br.com.garagem.revenda.shared.RevendaDb;
import br.com.garagem.tenancy.RequerModulo;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Quem está esperando uma resposta da revenda.
 *
 * <p>"Retornos" é o nome que o usuário vê nos dois módulos, mas o que se busca em cada um é coisa
 * diferente: a oficina procura orçamento sem resposta e revisão vencida; a revenda procura gente
 * que demonstrou interesse e parou de ouvir falar da loja. Compartilhar a implementação obrigaria
 * um dos dois a torcer o próprio vocabulário para caber no do outro — e o módulo de oficina
 * permaneceria exigido de quem só vende carro.
 *
 * <p>Por isso são dois endpoints com o mesmo nome de tela e módulos distintos. {@link RequerModulo}
 * segue valendo: uma empresa só de oficina recebe 404 daqui, e o contrário também.
 */
@RestController
@RequestMapping("/api/v1/revenda/retornos")
@RequerModulo(ModuloEmpresa.REVENDA)
@PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
@Transactional(readOnly = true)
public class RetornoRevendaController {
  /**
   * Um motivo de ligação. {@code desde} é o que responde à pergunta que importa — "parado há quanto
   * tempo?" —, e {@code telefone} vem junto para que chamar no WhatsApp não exija abrir o cadastro.
   */
  public record Retorno(
      String tipo,
      UUID id,
      String cliente,
      String telefone,
      String veiculo,
      String detalhe,
      Instant desde) {}

  private static final int PARADO_DIAS = 7;

  private final RevendaDb db;
  private final Clock clock;

  public RetornoRevendaController(RevendaDb db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  @GetMapping
  public List<Retorno> listar() {
    var limite = Instant.now(clock).minus(Duration.ofDays(PARADO_DIAS));
    var p = Map.<String, Object>of("limite", limite);
    // Uma consulta por motivo, unidas no banco: trazer quatro listas e juntar em memória custaria
    // quatro viagens e a ordenação sairia errada na primeira vez que uma delas paginasse.
    return db.rows(
        Retorno.class,
        """
        select 'INTERESSADO_SEM_RETORNO' tipo, l.id, c.nome cliente, c.telefone,
               concat_ws(' ', v.marca, v.modelo, v.placa) veiculo,
               'Sem contato desde a abertura' detalhe, l.criado_em desde
          from revenda_lead l
          join cliente c on c.id=l.cliente_id and c.oficina_id=l.oficina_id
          left join veiculo v on v.id=l.veiculo_id and v.oficina_id=l.oficina_id
         where l.oficina_id=:tenant and l.status='NOVO' and l.criado_em<=:limite
        union all
        select 'NEGOCIACAO_PARADA', l.id, c.nome, c.telefone,
               concat_ws(' ', v.marca, v.modelo, v.placa),
               'Negociação sem avanço', l.criado_em
          from revenda_lead l
          join cliente c on c.id=l.cliente_id and c.oficina_id=l.oficina_id
          left join veiculo v on v.id=l.veiculo_id and v.oficina_id=l.oficina_id
         where l.oficina_id=:tenant and l.status in ('CONTATO_REALIZADO','INTERESSADO','NEGOCIACAO')
           and l.criado_em<=:limite
        union all
        select 'PROPOSTA_EM_ABERTO', pr.id, c.nome, c.telefone,
               concat_ws(' ', v.marca, v.modelo, v.placa),
               'Proposta enviada sem resposta', pr.criado_em
          from revenda_proposta pr
          join cliente c on c.id=pr.cliente_id and c.oficina_id=pr.oficina_id
          join revenda_estoque e on e.id=pr.estoque_id and e.oficina_id=pr.oficina_id
          join veiculo v on v.id=e.veiculo_id and v.oficina_id=e.oficina_id
         where pr.oficina_id=:tenant and pr.status='ENVIADA' and pr.criado_em<=:limite
        union all
        select 'RESERVA_CANCELADA', r.id, c.nome, c.telefone,
               concat_ws(' ', v.marca, v.modelo, v.placa),
               'Reserva cancelada ou expirada', r.criado_em
          from revenda_reserva r
          join cliente c on c.id=r.cliente_id and c.oficina_id=r.oficina_id
          join revenda_estoque e on e.id=r.estoque_id and e.oficina_id=r.oficina_id
          join veiculo v on v.id=e.veiculo_id and v.oficina_id=e.oficina_id
         where r.oficina_id=:tenant and r.status in ('CANCELADA','EXPIRADA')
           and not exists (select 1 from revenda_venda s
                            where s.oficina_id=r.oficina_id and s.estoque_id=r.estoque_id)
        order by desde
        """,
        p);
  }
}
