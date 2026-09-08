package cl.duoc.campuslab.bookings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Prueba basica: el contexto arranca y las reservas de prueba quedan sembradas. */
@SpringBootTest
class BookingsApplicationTests {

    @Autowired
    private ReservaRepository repository;

    @Test
    void contextLoadsAndSeedsReservas() {
        assertThat(repository.count()).isGreaterThan(0);
    }
}
