package cl.duoc.campuslab.rabbitadmin.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Convierte el claim "cognito:groups" en authorities (grupo Admin -> ROLE_ADMIN). */
public class CognitoRolesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        if (jwt.getClaim("cognito:groups") instanceof List<?> grupos) {
            for (Object grupo : grupos) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + grupo.toString().toUpperCase(java.util.Locale.ROOT)));
            }
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
