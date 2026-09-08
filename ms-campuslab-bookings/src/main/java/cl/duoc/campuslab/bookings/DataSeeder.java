package cl.duoc.campuslab.bookings;

import java.time.LocalDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Carga reservas de prueba al iniciar para poder demostrar el CRUD y los estados. */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(ReservaRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            repository.save(new Reserva("Ana Perez", "12345678-9",
                    LocalDateTime.now().plusDays(1), 1L, EstadoReserva.SOLICITADA));
            repository.save(new Reserva("Bruno Soto", "9876543-2",
                    LocalDateTime.now().plusDays(2), 3L, EstadoReserva.APROBADA));
        };
    }
}
