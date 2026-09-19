package br.com.garagem.revenda.shared;

import br.com.garagem.shared.error.ApiException;
import br.com.garagem.shared.persistence.Pagina;
import br.com.garagem.tenancy.TenantContext;
import java.time.*;
import java.util.*;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Infraestrutura SQL do módulo. Projeções são records; nenhum estado gerenciado cruza a API. */
@Repository
public class RevendaDb {
  private final NamedParameterJdbcTemplate jdbc;
  private static final DefaultConversionService CONVERSIONS = new DefaultConversionService();

  static {
    CONVERSIONS.addConverter(
        java.sql.Timestamp.class, Instant.class, java.sql.Timestamp::toInstant);
    CONVERSIONS.addConverter(java.sql.Date.class, LocalDate.class, java.sql.Date::toLocalDate);
  }

  public RevendaDb(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public static Map<String, Object> params(Object... values) {
    var result = new HashMap<String, Object>();
    for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
    return result;
  }

  private Map<String, Object> scoped(String sql, Map<String, ?> values) {
    if (!sql.contains(":tenant"))
      throw new IllegalArgumentException("SQL de revenda deve declarar tenant");
    var params = new HashMap<String, Object>(values);
    params.put("tenant", TenantContext.current());
    params.replaceAll(
        (k, v) ->
            v instanceof Instant i
                ? java.sql.Timestamp.from(i)
                : v instanceof Enum<?> e ? e.name() : v);
    return params;
  }

  private <T> DataClassRowMapper<T> mapper(Class<T> type) {
    var mapper = new DataClassRowMapper<>(type);
    mapper.setConversionService(CONVERSIONS);
    return mapper;
  }

  public <T> T one(Class<T> type, String sql, Map<String, ?> params) {
    try {
      return jdbc.queryForObject(sql, scoped(sql, params), mapper(type));
    } catch (EmptyResultDataAccessException e) {
      throw ApiException.missing();
    }
  }

  public <T> List<T> rows(Class<T> type, String sql, Map<String, ?> params) {
    return jdbc.query(sql, scoped(sql, params), mapper(type));
  }

  public int update(String sql, Map<String, ?> params) {
    return jdbc.update(sql, scoped(sql, params));
  }

  public long count(String sql, Map<String, ?> params) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, scoped(sql, params), Long.class));
  }

  public <T> Pagina<T> page(
      Class<T> type,
      String select,
      String from,
      String order,
      Map<String, ?> params,
      int pagina,
      int tamanho) {
    var page = Pagina.request(pagina, tamanho);
    long total = count("select count(*) " + from, params);
    var values = new HashMap<String, Object>(params);
    values.put("limit", page.getPageSize());
    values.put("offset", page.getOffset());
    var rows =
        rows(
            type,
            select + " " + from + " order by " + order + " limit :limit offset :offset",
            values);
    return new Pagina<>(
        rows,
        page.getPageNumber(),
        page.getPageSize(),
        total,
        (int) ((total + page.getPageSize() - 1) / page.getPageSize()));
  }

  public static void revision(long actual, Long expected) {
    if (expected == null || actual != expected)
      throw ApiException.conflict("O registro mudou. Atualize os dados antes de confirmar.");
  }

  public static void filter(
      StringBuilder sql, Map<String, Object> params, String name, String expression, Object value) {
    if (value != null && !(value instanceof String s && s.isBlank())) {
      sql.append(" and ").append(expression);
      params.put(name, value);
    }
  }
}
