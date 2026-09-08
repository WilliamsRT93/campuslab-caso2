package cl.duoc.campuslab.bff;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Convierte el claim "cognito:groups" del token de Cognito en authorities de Spring Security.
 * Un usuario del grupo "Admin" obtiene la authority ROLE_ADMIN, y asi para cada grupo.
 */
public class CognitoRolesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        Object groups = jwt.getClaim("cognito:groups");
        if (groups instanceof List<?> lista) {
            for (Object grupo : lista) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + grupo.toString().toUpperCase()));
            }
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
