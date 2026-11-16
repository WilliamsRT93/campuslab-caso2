package cl.duoc.campuslab.reporteria.dto;

/**
 * Espejo del DTO que publica ms-campuslab-bookings en el topico campuslab.ventas.
 * Los microservicios son independientes (sin libreria compartida): el contrato es el
 * nombre/tipo de estos campos en el JSON, no la clase Java.
 */
public record VentaEventoDto(
        Long reservaId,
        String nombre,
        String rut,
        String fecha,
        Long productoId,
        String estado) {
}
