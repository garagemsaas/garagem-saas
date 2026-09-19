package br.com.garagem.tenancy;

import java.lang.annotation.*;

/**
 * Declara que o controlador serve qualquer empresa, independente dos módulos contratados.
 *
 * <p>Existe para que "não exige módulo" seja uma afirmação, e não um esquecimento. Identidade da
 * empresa, autenticação, usuários, clientes e veículos são vocabulário comum a OFICINA e REVENDA.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SemModulo {}
