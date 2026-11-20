package cl.duoc.campuslab.kafkaadmin.dto;

public record LagParticionResponse(
        String topico,
        int particion,
        long offsetComprometido,
        long offsetFinal,
        long lag) {
}
