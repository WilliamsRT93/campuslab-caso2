package cl.duoc.campuslab.bff;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad del BFF. Igual que hace el API Gateway, el BFF valida el JWT contra el IDaaS
 * (Cognito) y solo deja pasar la peticion si el token es valido y el rol tiene permiso.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Respuesta JSON uniforme cuando el token falta o es invalido (401). */
    private AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            String reason = authException.getMessage() == null || authException.getMessage().isBlank()
                    ? "No se proporciono un token valido"
                    : authException.getMessage();
            String escaped = reason.replace("\\", "\\\\").replace("\"", "\\\"");
            response.getWriter().print("{\"status\":401,\"error\":\"Unauthorized\","
                    + "\"message\":\"Autenticacion rechazada\",\"reason\":\"" + escaped + "\"}");
            response.getWriter().flush();
        };
    }

    /**
     * Perfil productivo (default): valida el JWT de Cognito y aplica autorizacion por rol.
     * - Lecturas del catalogo y reservas: cualquier usuario autenticado.
     * - Escrituras del catalogo (POST/PUT/DELETE): solo ROLE_ADMIN.
     * - Cambio de estado de reservas: ROLE_ADMIN u ROLE_OPERADOR.
     */
    @Bean
    @Profile("cognito")
    SecurityFilterChain cognitoSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/me").authenticated()
                        // Escrituras del catalogo: solo Admin
                        .requestMatchers(HttpMethod.POST, "/api/catalog/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/catalog/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/catalog/**").hasRole("ADMIN")
                        // Cambio de estado de reservas: Admin u Operador
                        .requestMatchers(HttpMethod.PUT, "/api/bookings/*/status").hasAnyRole("ADMIN", "OPERADOR")
                        // Resto de rutas de dominio: autenticado
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new CognitoRolesConverter())))
                .build();
    }

    /** Perfil local: sin autenticacion, para desarrollo y pruebas del ruteo. */
    @Bean
    @Profile("local")
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    /** CORS: solo el origen del frontend, con los metodos y headers necesarios (sin sobrepermisos). */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${campuslab.cors.allowed-origin:http://localhost:5173}") String allowedOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Decoder que valida el token de Cognito: firma (via JWKS del issuer), emisor, vigencia,
     * y ademas que sea un access token (token_use=access) del client_id autorizado.
     */
    @Bean
    @Profile("cognito")
    JwtDecoder cognitoJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${campuslab.cognito.client-id}") String clientId) {
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> cognitoValidator = jwt -> {
            if (!"access".equals(jwt.getClaimAsString("token_use"))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "El token debe ser un access token de Cognito", null));
            }
            if (!clientId.equals(jwt.getClaimAsString("client_id"))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "El client_id del token no esta autorizado", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, cognitoValidator));
        return decoder;
    }
}
