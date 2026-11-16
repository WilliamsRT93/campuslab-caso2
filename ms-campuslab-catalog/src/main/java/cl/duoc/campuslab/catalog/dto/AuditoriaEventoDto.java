package cl.duoc.campuslab.catalog.dto;

/**
 * Evento tipado que se publica en el topico Kafka campuslab.auditoria (EP5). Espejo del DTO
 * que espera ms-campuslab-reporteria. En una eliminacion, nombre/tipo/stock/disponible viajan
 * en null (el producto ya no existe).
 */
public record AuditoriaEventoDto(
        String accion,
        Long productoId,
        String nombre,
        String tipo,
        Integer stock,
        Boolean disponible) {
}
