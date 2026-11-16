package cl.duoc.campuslab.kafkaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.duoc.campuslab.kafkaadmin.dto.ActualizarConfigTopicoRequest;
import cl.duoc.campuslab.kafkaadmin.dto.CrearTopicoRequest;
import cl.duoc.campuslab.kafkaadmin.dto.TopicoResponse;
import cl.duoc.campuslab.kafkaadmin.exception.RecursoExistenteException;
import cl.duoc.campuslab.kafkaadmin.exception.RecursoNoEncontradoException;
import cl.duoc.campuslab.kafkaadmin.exception.SolicitudInvalidaException;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Pruebas contra un broker Kafka embebido real (no mocks de AdminClient: la API basada en
 * KafkaFuture es dificil de simular fielmente y un broker embebido prueba el comportamiento
 * real de cada operacion administrativa).
 */
@SpringJUnitConfig
@EmbeddedKafka(partitions = 1, topics = {})
class KafkaAdminServiceTest {

    @Autowired
    private EmbeddedKafkaBroker broker;

    private AdminClient adminClient;
    private KafkaAdminService service;

    @BeforeEach
    void iniciar() {
        adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString()));
        service = new KafkaAdminService(adminClient);
    }

    @AfterEach
    void cerrar() {
        adminClient.close();
    }

    @Test
    void creaYListaUnTopico() {
        service.crearTopico(new CrearTopicoRequest("topico-prueba-1", 2, (short) 1, 60000L, "delete"));

        TopicoResponse topico = service.obtenerTopico("topico-prueba-1");

        assertThat(topico.nombre()).isEqualTo("topico-prueba-1");
        assertThat(topico.particiones()).isEqualTo(2);
        assertThat(topico.configuracion()).containsEntry("retention.ms", "60000");
        assertThat(topico.configuracion()).containsEntry("cleanup.policy", "delete");
    }

    @Test
    void noPermiteCrearElMismoTopicoDosVeces() {
        service.crearTopico(new CrearTopicoRequest("topico-duplicado", 1, (short) 1, null, null));

        assertThatThrownBy(() ->
                service.crearTopico(new CrearTopicoRequest("topico-duplicado", 1, (short) 1, null, null)))
                .isInstanceOf(RecursoExistenteException.class);
    }

    @Test
    void eliminarUnTopicoInexistenteLanzaNoEncontrado() {
        assertThatThrownBy(() -> service.eliminarTopico("no-existe-jamas"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void actualizaLaRetencionDeUnTopicoExistente() {
        service.crearTopico(new CrearTopicoRequest("topico-config", 1, (short) 1, 1000L, "delete"));

        service.actualizarConfiguracion("topico-config", new ActualizarConfigTopicoRequest(999999L, null));

        TopicoResponse topico = service.obtenerTopico("topico-config");
        assertThat(topico.configuracion()).containsEntry("retention.ms", "999999");
    }

    @Test
    void aumentarParticionesPorDebajoDelActualFalla() {
        service.crearTopico(new CrearTopicoRequest("topico-particiones", 3, (short) 1, null, null));

        assertThatThrownBy(() -> service.aumentarParticiones("topico-particiones", 2))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void aumentaParticionesCorrectamente() {
        service.crearTopico(new CrearTopicoRequest("topico-crece", 1, (short) 1, null, null));

        service.aumentarParticiones("topico-crece", 4);

        assertThat(service.obtenerTopico("topico-crece").particiones()).isEqualTo(4);
    }
}
