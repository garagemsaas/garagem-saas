package br.com.garagem.shared.persistence;

import java.util.Locale;

/** Normalização dos filtros textuais das listagens. */
public final class Filtros {
  private Filtros() {}

  /**
   * Converte texto livre em padrão {@code like} minúsculo e neutraliza os coringas do SQL, de forma
   * que "100%" procure o literal e não qualquer sufixo. Devolve {@code null} quando o filtro não
   * foi informado, para que a consulta ignore o predicado.
   */
  public static String like(String valor) {
    if (valor == null || valor.isBlank()) return null;
    return "%"
        + valor
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")
        + "%";
  }

  public static String texto(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }
}
