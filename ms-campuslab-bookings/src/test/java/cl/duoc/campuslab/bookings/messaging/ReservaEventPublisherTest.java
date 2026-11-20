package cl.duoc.campuslab.bookings.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.campuslab.bookings.EstadoReserva;
import cl.duoc.campuslab.bookings.Reserva;
import cl.duoc.campuslab.bookings.config.KafkaMessagingProperties;
import cl.duoc.campuslab.bookings.config.RabbitMessagingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Verifica que, ademas de RabbitMQ, cada reserva creada se publique como evento tipado en el
 * topico Kafka de ventas (EP5) -- sin tocar la logica ya probada de RabbitMQ.
 */
class ReservaEventPublisherTest {

    private static final RabbitMessagingProperties RABBIT_PROPS = new RabbitMessagingProperties(
            "campuslab.exchange", "cola-notificaciones", "cola-documentos",
            "reserva.notificacion", "reserva.documento",
            "campuslab.eventos", "cola-eventos-reservas", "reserva.creada");

    private static final KafkaMessagingProperties KAFKA_PROPS = new KafkaMessagingProperties("campuslab.ventas");

    @SuppressWarnings("unchecked")
    @Test
    void publicaElEventoDeVentaEnElTopicoKafkaConfigurado() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        SendResult<String, String> sendResult = new SendResult<>(
                new org.apache.kafka.clients.producer.ProducerRecord<>("campuslab.ventas", "1", "{}"),
                new RecordMetadata(new TopicPartition("campuslab.ventas", 0), 0, 0, 0, 0, 0));
        when(kafkaTemplate.send(eq("campuslab.ventas"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        ReservaEventPublisher publisher = new ReservaEventPublisher(
                rabbitTemplate, RABBIT_PROPS, kafkaTemplate, KAFKA_PROPS, new ObjectMapper());

        Reserva reserva = new Reserva("Juan Perez", "11111111-1", LocalDateTime.of(2026, 11, 20, 10, 0),
                5L, EstadoReserva.SOLICITADA);
        reserva.setId(42L);

        publisher.publicarReservaCreada(reserva);

        verify(kafkaTemplate).send(eq("campuslab.ventas"), eq("42"), argThatContieneLaReserva());
    }

    private static String argThatContieneLaReserva() {
        return org.mockito.ArgumentMatchers.argThat(json ->
                json.contains("\"reservaId\":42") && json.contains("\"estado\":\"SOLICITADA\""));
    }

    @SuppressWarnings("unchecked")
    @Test
    void publicaDeNuevoEnKafkaCuandoCambiaElEstadoSinTocarRabbit() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        SendResult<String, String> sendResult = new SendResult<>(
                new org.apache.kafka.clients.producer.ProducerRecord<>("campuslab.ventas", "42", "{}"),
                new RecordMetadata(new TopicPartition("campuslab.ventas", 0), 0, 0, 0, 0, 0));
        when(kafkaTemplate.send(eq("campuslab.ventas"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        ReservaEventPublisher publisher = new ReservaEventPublisher(
                rabbitTemplate, RABBIT_PROPS, kafkaTemplate, KAFKA_PROPS, new ObjectMapper());

        Reserva reserva = new Reserva("Juan Perez", "11111111-1", LocalDateTime.of(2026, 11, 20, 10, 0),
                5L, EstadoReserva.APROBADA);
        reserva.setId(42L);

        publisher.publicarCambioEstado(reserva);

        verify(kafkaTemplate).send(eq("campuslab.ventas"), eq("42"), org.mockito.ArgumentMatchers.argThat(
                (String json) -> json.contains("\"estado\":\"APROBADA\"")));
        org.mockito.Mockito.verifyNoInteractions(rabbitTemplate);
    }
}
