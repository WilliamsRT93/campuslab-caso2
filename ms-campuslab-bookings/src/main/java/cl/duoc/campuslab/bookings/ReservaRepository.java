package cl.duoc.campuslab.bookings;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio JPA de reservas. */
public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    List<Reserva> findByEstado(EstadoReserva estado);
}
