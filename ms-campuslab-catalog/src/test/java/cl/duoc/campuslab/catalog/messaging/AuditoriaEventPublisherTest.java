package cl.duoc.campuslab.catalog.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.campuslab.catalog.Producto;
import cl.duoc.campuslab.catalog.config.KafkaMessagingProperties;
import cl.duoc.campuslab.catalog.config.RabbitMessagingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Verifica que, ademas de RabbitMQ, cada creacion/eliminacion de producto se publique como
 * evento tipado en el topico Kafka de auditoria (EP5) -- sin tocar la logica ya probada de
 * RabbitMQ.
 */
class AuditoriaEventPublisherTest {

    private static final RabbitMessagingProperties RABBIT_PROPS = new RabbitMessagingProperties(
            "campuslab.exchange", "cola-auditoria", "reserva.auditoria",
            "campuslab.eventos", "cola-eventos-catalogo", "catalogo.producto");

    private static final KafkaMessagingProperties KAFKA_PROPS = new KafkaMessagingProperties("campuslab.auditoria");

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, String> kafkaTemplateQueResponde() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        SendResult<String, String> sendResult = new SendResult<>(
                new ProducerRecord<>("campuslab.auditoria", "1", "{}"),
                new RecordMetadata(new TopicPartition("campuslab.auditoria", 0), 0, 0, 0, 0, 0));
        when(kafkaTemplate.send(eq("campuslab.auditoria"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        return kafkaTemplate;
    }

    @Test
    void publicaElEventoDeCreacionEnElTopicoKafkaConfigurado() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateQueResponde();

        AuditoriaEventPublisher publisher = new AuditoriaEventPublisher(
                rabbitTemplate, RABBIT_PROPS, kafkaTemplate, KAFKA_PROPS, new ObjectMapper());

        Producto producto = new Producto();
        producto.setId(7L);
        producto.setNombre("Laptop");
        producto.setTipo("ELECTRONICO");
        producto.setStock(10);
        producto.setDisponible(true);

        publisher.publicarEvento("CREADO", producto);

        verify(kafkaTemplate).send(eq("campuslab.auditoria"), eq("7"), argThat((String json) ->
                json.contains("\"accion\":\"CREADO\"") && json.contains("\"productoId\":7")));
    }

    @Test
    void publicaLaEliminacionConCamposNulos() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateQueResponde();

        AuditoriaEventPublisher publisher = new AuditoriaEventPublisher(
                rabbitTemplate, RABBIT_PROPS, kafkaTemplate, KAFKA_PROPS, new ObjectMapper());

        publisher.publicarEliminacion("ELIMINADO", 7L);

        verify(kafkaTemplate).send(eq("campuslab.auditoria"), eq("7"), argThat((String json) ->
                json.contains("\"accion\":\"ELIMINADO\"") && json.contains("\"nombre\":null")));
    }
}
