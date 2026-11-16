package cl.duoc.campuslab.reporteria.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.duoc.campuslab.reporteria.repository.EventoAuditoriaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class AuditoriaEventosListenerTest {

    @Autowired
    private EventoAuditoriaRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void guardaUnEventoDeAuditoriaValido() {
        AuditoriaEventosListener listener = new AuditoriaEventosListener(repository, objectMapper);
        String payload = """
                {"accion":"CREADO","productoId":9,"nombre":"Laptop","tipo":"ELECTRONICO",
                "stock":10,"disponible":true}""";

        listener.escuchar(payload);

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findAll().get(0).getAccion()).isEqualTo("CREADO");
        assertThat(repository.findAll().get(0).getProductoId()).isEqualTo(9L);
    }

    @Test
    void guardaUnaEliminacionConCamposNulos() {
        AuditoriaEventosListener listener = new AuditoriaEventosListener(repository, objectMapper);
        String payload = """
                {"accion":"ELIMINADO","productoId":9,"nombre":null,"tipo":null,
                "stock":null,"disponible":null}""";

        listener.escuchar(payload);

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findAll().get(0).getNombre()).isNull();
    }

    @Test
    void payloadInvalidoNoEsRecuperable() {
        AuditoriaEventosListener listener = new AuditoriaEventosListener(repository, objectMapper);

        assertThatThrownBy(() -> listener.escuchar("{incompleto"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
