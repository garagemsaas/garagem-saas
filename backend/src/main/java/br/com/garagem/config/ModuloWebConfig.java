package br.com.garagem.config;

import br.com.garagem.tenancy.ModuloInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Liga a exigência de módulo ao MVC. Sem padrão de caminho: o interceptador vê todo handler e
 * decide pela anotação do próprio endpoint, que é o ponto da troca — registrar caminhos aqui
 * recriaria a lista central que se queria eliminar.
 */
@Configuration
public class ModuloWebConfig implements WebMvcConfigurer {
  private final JdbcTemplate jdbc;

  public ModuloWebConfig(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new ModuloInterceptor(jdbc));
  }
}
