package br.com.garagem.tenancy;

import java.util.UUID;

public final class TenantContext {
  public static final UUID UNRESOLVED = new UUID(0, 0);
  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  public static UUID current() {
    UUID id = CURRENT.get();
    if (id == null || UNRESOLVED.equals(id))
      throw new IllegalStateException("Oficina não resolvida");
    return id;
  }

  public static UUID resolvedOrEmpty() {
    return CURRENT.get() == null ? UNRESOLVED : CURRENT.get();
  }

  public static void set(UUID id) {
    CURRENT.set(id);
  }

  public static void clear() {
    CURRENT.remove();
  }
}
