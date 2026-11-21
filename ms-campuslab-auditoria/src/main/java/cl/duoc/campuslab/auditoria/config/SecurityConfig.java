package cl.duoc.campuslab.auditoria.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del registro de auditoria: valida el JWT de Cognito y solo permite el rol Admin
 * en /api/reporteria/auditoria (misma ruta que antes, mismo IDaaS y patron que los demas
 * microservicios -- solo cambia el microservicio que la atiende).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static AuthenticationEntryPoint sinToken() {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().print("{\"status\":401,\"error\":\"Unauthorized\","
                    + "\"message\":\"Falta un token JWT valido de Cognito.\"}");
        };
    }

    @Bean
    @ConditionalOnProperty(name = "campuslab.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain protegida(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .requestMatchers("/api/reporteria/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(sinToken()))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(sinToken())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new CognitoRolesConverter())))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "campuslab.security.enabled", havingValue = "false")
    SecurityFilterChain abierta(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "campuslab.security.enabled", havingValue = "true", matchIfMissing = true)
    JwtDecoder jwtDecoder(@Value("${campuslab.security.jwk-set-uri}") String jwkSetUri,
                          @Value("${campuslab.security.issuer}") String issuer,
                          @Value("${campuslab.security.client-id}") String clientId) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> accesoDeLaApp = jwt ->
                "access".equals(jwt.getClaimAsString("token_use")) && clientId.equals(jwt.getClaimAsString("client_id"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "token_use o client_id invalidos", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), accesoDeLaApp));
        return decoder;
    }
}
