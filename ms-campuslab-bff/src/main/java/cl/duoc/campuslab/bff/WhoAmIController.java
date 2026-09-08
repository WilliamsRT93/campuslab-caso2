package cl.duoc.campuslab.bff;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Devuelve la identidad y los roles derivados del token. Util para que el frontend
 * sepa si el usuario es Admin y para demostrar en Postman los claims validados.
 */
@RestController
public class WhoAmIController {

    @GetMapping("/api/me")
    public Map<String, Object> me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        String username = authentication.getName();
        String email = null;
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            email = jwt.getClaimAsString("email");
            String preferred = jwt.getClaimAsString("username");
            if (preferred != null) {
                username = preferred;
            }
        }

        return Map.of(
                "username", username,
                "email", email != null ? email : "",
                "roles", roles,
                "isAdmin", roles.contains("ROLE_ADMIN"));
    }
}
