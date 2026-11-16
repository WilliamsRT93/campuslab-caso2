package cl.duoc.campuslab.kafkaadmin.dto;

public record PublicarPruebaKafkaResponse(
        String topico,
        String payloadEnviado,
        int particion,
        long offset,
        String detalle) {
}
