package br.com.garagem.revenda.dashboard;

import br.com.garagem.oficina.ModuloEmpresa;
import br.com.garagem.revenda.estoque.EstoqueService;
import br.com.garagem.revenda.shared.RevendaDb;
import br.com.garagem.shared.error.ApiException;
import br.com.garagem.tenancy.*;
import java.math.BigDecimal;
import java.time.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/revenda/dashboard")
@RequerModulo(ModuloEmpresa.REVENDA)
@PreAuthorize("hasAnyRole('OWNER','ATENDENTE')")
public class RevendaDashboardController {
  public record Resumo(
      long emAvaliacao,
      long emPreparacao,
      long disponiveis,
      long reservados,
      long vendidos,
      BigDecimal valorAquisicao,
      BigDecimal custosPreparacao,
      BigDecimal valorAnunciado,
      BigDecimal margemPotencial,
      BigDecimal mediaDiasEstoque,
      long leadsAbertos,
      long propostasAbertas,
      long vendasPeriodo,
      BigDecimal valorVendido,
      BigDecimal margemRealizada) {}

  private final RevendaDb db;
  private final EstoqueService estoques;
  private final Clock clock;

  public RevendaDashboardController(RevendaDb db, EstoqueService estoques, Clock clock) {
    this.db = db;
    this.estoques = estoques;
    this.clock = clock;
  }

  @GetMapping
  @Transactional
  public Resumo resumo(
      @RequestParam(required = false) Instant de, @RequestParam(required = false) Instant ate) {
    Instant fim = ate == null ? clock.instant() : ate;
    Instant inicio = de == null ? fim.minus(Duration.ofDays(30)) : de;
    if (!inicio.isBefore(fim)) throw ApiException.invalid("Período inválido.");
    estoques.expirarReservas();
    return db.one(
        Resumo.class,
        """
      with custos as (select estoque_id,sum(valor) valor from revenda_custo where oficina_id=:tenant group by estoque_id),
      estoque as (select e.*,coalesce(c.valor,0) custo from revenda_estoque e left join custos c on c.estoque_id=e.id where e.oficina_id=:tenant)
      select count(*) filter(where status='EM_AVALIACAO') em_avaliacao,
      count(*) filter(where status='EM_PREPARACAO') em_preparacao,count(*) filter(where status='DISPONIVEL') disponiveis,
      count(*) filter(where status='RESERVADO') reservados,count(*) filter(where status='VENDIDO') vendidos,
      coalesce(sum(valor_aquisicao) filter(where status<>'VENDIDO'),0) valor_aquisicao,
      coalesce(sum(custo) filter(where status<>'VENDIDO'),0) custos_preparacao,
      coalesce(sum(preco_anunciado) filter(where status<>'VENDIDO'),0) valor_anunciado,
      coalesce(sum(preco_anunciado-valor_aquisicao-custo) filter(where status<>'VENDIDO'),0) margem_potencial,
      coalesce(avg(cast(:hoje as date)-entrada) filter(where status<>'VENDIDO'),0) media_dias_estoque,
      (select count(*) from revenda_lead where oficina_id=:tenant and status not in ('VENDIDO','PERDIDO')) leads_abertos,
      (select count(*) from revenda_proposta p join revenda_proposta_versao pv on pv.proposta_id=p.id and pv.oficina_id=p.oficina_id and pv.numero=p.numero_versao where p.oficina_id=:tenant and p.status in ('RASCUNHO','ENVIADA') and pv.validade>:agora) propostas_abertas,
      (select count(*) from revenda_venda where oficina_id=:tenant and criado_em>=:de and criado_em<:ate) vendas_periodo,
      (select coalesce(sum(valor_vendido),0) from revenda_venda where oficina_id=:tenant and criado_em>=:de and criado_em<:ate) valor_vendido,
      (select coalesce(sum(margem_bruta),0) from revenda_venda where oficina_id=:tenant and criado_em>=:de and criado_em<:ate) margem_realizada
      from estoque
      """,
        RevendaDb.params(
            "de", inicio, "ate", fim, "hoje", LocalDate.now(clock), "agora", clock.instant()));
  }
}
