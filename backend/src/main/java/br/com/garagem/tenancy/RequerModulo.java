package br.com.garagem.tenancy;

import br.com.garagem.oficina.ModuloEmpresa;
import java.lang.annotation.*;

/**
 * Declara que o endpoint só existe para empresas que contrataram determinado módulo.
 *
 * <p>Antes essa decisão vivia numa expressão regular de caminhos dentro de {@link
 * TenantRequestFilter}. Funcionava, mas a proteção morava longe do que ela protege: criar {@code
 * /api/v1/ordens-servico/{id}/checklist} e esquecer de somar o caminho à regex entregaria a
 * funcionalidade a uma empresa que só contratou REVENDA, sem quebrar teste nem compilação.
 *
 * <p>Aqui a exigência é do próprio controlador. Vale na classe — e todo método herda — e pode ser
 * apertada em um método específico. Quem não exige módulo algum declara {@link SemModulo}, e {@code
 * ContratoDeModuloTest} recusa um {@code @RestController} que não diga uma coisa ou outra: a
 * escolha é obrigatória, nunca o silêncio.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequerModulo {
  ModuloEmpresa value();
}
