package cl.duoc.campuslab.auditoria.dto;

/**
 * Espejo del DTO que publica ms-campuslab-catalog en el topico campuslab.auditoria.
 * En una eliminacion, nombre/tipo/stock/disponible viajan en null (el producto ya no existe).
 */
public record AuditoriaEventoDto(
        String accion,
        Long productoId,
        String nombre,
        String tipo,
        Integer stock,
        Boolean disponible) {
}
