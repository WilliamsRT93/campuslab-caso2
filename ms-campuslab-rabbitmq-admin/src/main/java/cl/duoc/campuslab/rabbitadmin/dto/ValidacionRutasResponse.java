package cl.duoc.campuslab.rabbitadmin.dto;

import java.util.List;

/** Resultado de validar, desde fuera, las rutas del API Gateway. */
public record ValidacionRutasResponse(
        String apiGateway,
        int rutasDeclaradas,
        int rutasCorrectas,
        boolean todasProtegidasConJwt,
        boolean sinProxy,
        boolean ok,
        String conclusion,
        List<RutaResultado> rutas,
        List<ControlResultado> controles) {

    public record RutaResultado(String metodo, String ruta, int esperadoConToken, Integer sinToken, Integer conToken,
                                boolean ok) {}

    public record ControlResultado(String metodo, String ruta, Integer sinToken, Integer conToken, boolean ok) {}
}
