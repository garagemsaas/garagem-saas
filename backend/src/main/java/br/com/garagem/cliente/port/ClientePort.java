package br.com.garagem.cliente.port;

import java.util.UUID;

/** Presença de um cliente na oficina autenticada. É tudo que outro módulo precisa saber. */
public interface ClientePort {
  boolean existe(UUID clienteId);
}
