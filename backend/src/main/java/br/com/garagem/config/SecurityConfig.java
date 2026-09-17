package br.com.garagem.config;

import br.com.garagem.shared.error.ErrorCodes;
import br.com.garagem.shared.error.ProblemJson;
import br.com.garagem.tenancy.TenantRequestFilter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecretKeySpec jwtKey(@Value("${app.jwt.secret}") String secret) {
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32)
      throw new IllegalArgumentException("JWT_SECRET deve conter pelo menos 32 bytes");
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder encoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder decoder(SecretKeySpec key) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> audience =
        jwt ->
            jwt.getAudience().contains("garagem-api")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer("garagem-api"), audience));
    return decoder;
  }

  /**
   * Origens explícitas, sem credenciais de navegador: a API autentica por cabeçalho Authorization,
   * nunca por cookie, então não há por que ligar allowCredentials. Sem origens configuradas, nenhum
   * cabeçalho CORS é emitido.
   */
  @Bean
  CorsConfigurationSource corsSource(CorsProperties properties) {
    var config = new CorsConfiguration();
    config.setAllowedOrigins(properties.allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
    config.setExposedHeaders(List.of("X-Request-Id"));
    config.setAllowCredentials(false);
    config.setMaxAge(3600L);
    var source = new UrlBasedCorsConfigurationSource();
    if (!properties.allowedOrigins().isEmpty()) source.registerCorsConfiguration("/**", config);
    LoggerFactory.getLogger(SecurityConfig.class)
        .atInfo()
        .addKeyValue("origens_cors", properties.allowedOrigins().size())
        .log("cors_configurado");
    return source;
  }

  @Bean
  SecurityFilterChain security(
      HttpSecurity http, JdbcTemplate jdbc, CorsConfigurationSource corsSource) throws Exception {
    var roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("papel");
    roles.setAuthorityPrefix("ROLE_");
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(roles);
    return http.csrf(csrf -> csrf.disable())
        .cors(c -> c.configurationSource(corsSource))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/auth/**",
                        "/api/v1/publico/**",
                        // O gateway não tem sessão: a autenticidade vem da assinatura HMAC do
                        // corpo, conferida em WebhookPagamentoController antes de qualquer efeito.
                        "/api/v1/webhooks/pagamento",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/actuator/health",
                        "/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((req, res, error) -> failure(res, 401))
                    .accessDeniedHandler((req, res, error) -> failure(res, 403)))
        .oauth2ResourceServer(
            o ->
                o.authenticationEntryPoint(
                        (req, res, error) -> {
                          org.slf4j.MDC.put("auth_falha", "BEARER_INVALIDO_OU_EXPIRADO");
                          failure(res, 401);
                        })
                    .jwt(j -> j.jwtAuthenticationConverter(converter)))
        .addFilterAfter(new TenantRequestFilter(jdbc), BearerTokenAuthenticationFilter.class)
        .headers(
            h ->
                h.frameOptions(f -> f.deny())
                    .referrerPolicy(
                        r ->
                            r.policy(
                                org.springframework.security.web.header.writers
                                    .ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
        .build();
  }

  /** Mesmo corpo RFC 7807 do resto da API, para o frontend tratar 401/403 de um jeito só. */
  private static void failure(jakarta.servlet.http.HttpServletResponse response, int code)
      throws java.io.IOException {
    if (code == 401) {
      if (org.slf4j.MDC.get("auth_falha") == null)
        org.slf4j.MDC.put("auth_falha", "SESSAO_AUSENTE");
      response.setHeader("WWW-Authenticate", "Bearer");
      ProblemJson.write(
          response, 401, ErrorCodes.UNAUTHORIZED, "Autenticação necessária ou sessão expirada.");
    } else {
      org.slf4j.MDC.put("auth_falha", "PAPEL_NAO_AUTORIZADO");
      ProblemJson.write(response, 403, ErrorCodes.FORBIDDEN, "Seu papel não permite esta ação.");
    }
  }
}
