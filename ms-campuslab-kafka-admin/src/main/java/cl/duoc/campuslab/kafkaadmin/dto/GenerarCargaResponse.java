package cl.duoc.campuslab.kafkaadmin.dto;

public record GenerarCargaResponse(
        String topico,
        int mensajesPublicados,
        long duracionMs,
        String detalle) {
}
