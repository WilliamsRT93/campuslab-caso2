package cl.duoc.campuslab.rabbitadmin.dto;

import java.util.List;

/** Resultado de validar las colas de negocio: listener activo, bindings, DLX, TTL y (opcional) prueba extremo a extremo. */
public record ValidacionColasResponse(boolean ok, boolean probado, String conclusion, List<ColaValidada> colas) {

    public record ColaValidada(
            String cola,
            String exchange,
            String routingKey,
            boolean existe,
            long consumidores,
            boolean listenerActivo,
            boolean bindingPrincipal,
            boolean tieneDlx,
            boolean tieneTtl,
            String colaMuerta,
            boolean colaMuertaConBinding,
            boolean configuracionOk,
            Prueba prueba,
            boolean ok) {}

    /** Prueba extremo a extremo: se publica un mensaje normal y se espera que el listener lo consuma sin mandarlo a la DLQ. */
    public record Prueba(boolean publicado, boolean confirmado, boolean consumida, long dlqAntes, long dlqDespues,
                         boolean enviadaADlq, String detalle) {}
}
