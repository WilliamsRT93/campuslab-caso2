package cl.duoc.campuslab.kafkaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.duoc.campuslab.kafkaadmin.dto.ActualizarConfigTopicoRequest;
import cl.duoc.campuslab.kafkaadmin.dto.ConsumerGroupResponse;
import cl.duoc.campuslab.kafkaadmin.dto.CrearTopicoRequest;
import cl.duoc.campuslab.kafkaadmin.dto.TopicoResponse;
import cl.duoc.campuslab.kafkaadmin.exception.RecursoExistenteException;
import cl.duoc.campuslab.kafkaadmin.exception.RecursoNoEncontradoException;
import cl.duoc.campuslab.kafkaadmin.exception.SolicitudInvalidaException;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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

    @Test
    void calculaElConsumerLagEntreElOffsetFinalYElComprometido() {
        String topico = "topico-lag";
        String grupo = "grupo-lag";
        service.crearTopico(new CrearTopicoRequest(topico, 1, (short) 1, null, null));

        try (KafkaProducer<String, String> productor = productor()) {
            for (int i = 0; i < 5; i++) {
                productor.send(new ProducerRecord<>(topico, "clave-" + i, "valor-" + i));
            }
            productor.flush();
        }

        // El consumer group solo confirma (commit) los primeros 2 de los 5 mensajes -> lag esperado = 3.
        // Se usa assign()+commitSync(offset) en vez de subscribe()+poll() para fijar el offset
        // comprometido de forma determinista, sin depender de cuantos registros trae cada poll().
        TopicPartition particion = new TopicPartition(topico, 0);
        try (KafkaConsumer<String, String> consumidor = consumidor(grupo)) {
            consumidor.assign(List.of(particion));
            consumidor.commitSync(Map.of(particion, new OffsetAndMetadata(2L)));
        }

        List<ConsumerGroupResponse> grupos = service.listarConsumerGroups();
        ConsumerGroupResponse respuesta = grupos.stream()
                .filter(g -> g.groupId().equals(grupo))
                .findFirst()
                .orElseThrow();

        assertThat(respuesta.lagTotal()).isEqualTo(3L);
        assertThat(respuesta.detalleLag()).hasSize(1);
        assertThat(respuesta.detalleLag().get(0).topico()).isEqualTo(topico);
        assertThat(respuesta.detalleLag().get(0).offsetFinal()).isEqualTo(5L);
    }

    private KafkaProducer<String, String> productor() {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaProducer<>(config);
    }

    private KafkaConsumer<String, String> consumidor(String groupId) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return new KafkaConsumer<>(config);
    }
}
