package cl.duoc.campuslab.bookings;

/** Ciclo de vida de una reserva segun el caso CampusLab. */
public enum EstadoReserva {
    SOLICITADA,
    APROBADA,
    EN_PREPARACION,
    EN_USO,
    DEVUELTA,
    CANCELADA
}
