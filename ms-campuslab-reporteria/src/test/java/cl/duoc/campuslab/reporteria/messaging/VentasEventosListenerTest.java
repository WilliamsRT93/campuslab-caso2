package cl.duoc.campuslab.reporteria.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.duoc.campuslab.reporteria.repository.ReporteVentaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/**
 * Prueba el listener de ventas contra un repositorio JPA real (H2 en memoria, via @DataJpaTest),
 * verificando el camino feliz y el camino de error NO recuperable (payload invalido).
 */
@DataJpaTest
class VentasEventosListenerTest {

    @Autowired
    private ReporteVentaRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void guardaUnaVentaValida() {
        VentasEventosListener listener = new VentasEventosListener(repository, objectMapper);
        String payload = """
                {"reservaId":1,"nombre":"Juan Perez","rut":"11111111-1","fecha":"2026-11-20",
                "productoId":5,"estado":"PENDIENTE"}""";

        listener.escuchar(payload);

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findAll().get(0).getReservaId()).isEqualTo(1L);
        assertThat(repository.findAll().get(0).getEstado()).isEqualTo("PENDIENTE");
    }

    @Test
    void payloadInvalidoNoEsRecuperable() {
        VentasEventosListener listener = new VentasEventosListener(repository, objectMapper);

        assertThatThrownBy(() -> listener.escuchar("esto-no-es-json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findAll()).isEmpty();
    }
}
