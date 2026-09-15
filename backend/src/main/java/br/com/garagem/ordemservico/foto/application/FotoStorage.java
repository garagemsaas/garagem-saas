package br.com.garagem.ordemservico.foto.application;

public interface FotoStorage {
  void put(String key, byte[] content, String contentType);

  byte[] get(String key);

  void delete(String key);

  /** Sonda barata para o health check: confirma o destino sem ler nenhum objeto. */
  default boolean disponivel() {
    return true;
  }
}
