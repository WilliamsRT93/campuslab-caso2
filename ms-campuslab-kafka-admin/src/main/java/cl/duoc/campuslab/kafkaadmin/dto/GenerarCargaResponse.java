package cl.duoc.campuslab.kafkaadmin.dto;

public record GenerarCargaResponse(
        String topico,
        int mensajesPublicados,
        int validos,
        int malformados,
        long duracionMs,
        String detalle) {
}
