package cl.duoc.campuslab.kafkaadmin.dto;

import java.util.Map;

public record TopicoResponse(
        String nombre,
        int particiones,
        int factorReplicacion,
        Map<String, String> configuracion) {
}
