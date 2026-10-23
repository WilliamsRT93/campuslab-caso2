package cl.duoc.campuslab.rabbitadmin.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rutas del API Gateway que el validador comprueba (campuslab.validacion en application.yml).
 * "rutas" son las 14 declaradas en CloudFormation y "controles" son rutas que NO existen: si el gateway
 * responde 404 a todas, no hay ninguna ruta proxy ({proxy+} / ANY).
 */
@ConfigurationProperties(prefix = "campuslab.validacion")
public record ValidacionProperties(
        String apiGatewayUrl,
        int timeoutSegundos,
        List<RutaEsperada> rutas,
        List<RutaControl> controles) {

    /** Ruta declarada: con token valido debe responder "esperado" (200 o 400 con cuerpo vacio) y sin token 401. */
    public record RutaEsperada(String metodo, String ruta, String cuerpo, int esperado) {}

    /** Ruta no declarada: el gateway debe responder 404. */
    public record RutaControl(String metodo, String ruta) {}
}
