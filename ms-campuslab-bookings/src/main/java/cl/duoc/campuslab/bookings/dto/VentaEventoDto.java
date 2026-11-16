package cl.duoc.campuslab.bookings.dto;

/**
 * Evento tipado que se publica en el topico Kafka campuslab.ventas (EP5). Espejo del DTO que
 * espera ms-campuslab-reporteria (microservicios independientes, sin libreria compartida: el
 * contrato es el nombre/tipo de estos campos en el JSON, no la clase Java).
 */
public record VentaEventoDto(
        Long reservaId,
        String nombre,
        String rut,
        String fecha,
        Long productoId,
        String estado) {
}
