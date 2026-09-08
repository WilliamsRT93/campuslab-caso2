package cl.duoc.campuslab.bookings;

import jakarta.validation.constraints.NotNull;

/** Cuerpo para el cambio de estado de una reserva. */
public class StatusRequest {

    @NotNull(message = "El estado es obligatorio")
    private EstadoReserva status;

    public EstadoReserva getStatus() {
        return status;
    }

    public void setStatus(EstadoReserva status) {
        this.status = status;
    }
}
