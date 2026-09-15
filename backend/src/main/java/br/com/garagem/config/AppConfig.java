package br.com.garagem.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
  /** Relógio único da aplicação, para que os testes possam fixar o instante. */
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
