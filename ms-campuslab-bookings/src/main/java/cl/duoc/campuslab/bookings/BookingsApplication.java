package cl.duoc.campuslab.bookings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de reservas de CampusLab.
 * Expone el CRUD de reservas y el cambio de estado del ciclo de vida.
 * Es un servicio interno: solo lo consume el BFF.
 */
@SpringBootApplication
public class BookingsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingsApplication.class, args);
    }
}
