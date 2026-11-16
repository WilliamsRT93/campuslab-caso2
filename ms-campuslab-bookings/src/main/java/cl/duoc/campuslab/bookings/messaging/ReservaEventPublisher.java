package cl.duoc.campuslab.bookings.messaging;

import cl.duoc.campuslab.bookings.Reserva;
import cl.duoc.campuslab.bookings.config.KafkaMessagingProperties;
import cl.duoc.campuslab.bookings.config.RabbitMessagingProperties;
import cl.duoc.campuslab.bookings.dto.VentaEventoDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * PRODUCTOR de mensajes. Tras crear una reserva, publica dos eventos al
 * exchange principal: uno para que el worker de notificaciones avise al
 * usuario, y otro para que el worker de documentos genere el ticket/PDF.
 * Ademas publica el evento de VENTA en Kafka (EP5) para alimentar el panel de reportes
 * en tiempo real de ms-campuslab-reporteria -- este microservicio SOLO produce ese topico,
 * nunca lo consume (regla de diseno: productor y consumidor nunca son el mismo servicio).
 *
 * Esta clase NO conoce nombres de colas/topicos en crudo -- todo viene de
 * RabbitMessagingProperties/KafkaMessagingProperties (centralizados en application.yml).
 */
@Service
public class ReservaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReservaEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMessagingProperties props;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaMessagingProperties kafkaProps;
    private final ObjectMapper objectMapper;

    public ReservaEventPublisher(RabbitTemplate rabbitTemplate, RabbitMessagingProperties props,
            KafkaTemplate<String, String> kafkaTemplate, KafkaMessagingProperties kafkaProps,
            ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProps = kafkaProps;
        this.objectMapper = objectMapper;
    }

    public void publicarReservaCreada(Reserva reserva) {
        String payload = construirPayload(reserva);

        rabbitTemplate.convertAndSend(props.exchange(), props.routingKeyNotificaciones(), payload,
                new CorrelationData("reserva-" + reserva.getId() + "-notificacion"));
        log.info("✅ Evento publicado -> {} (routingKey={}) para reserva #{}",
                props.queueNotificaciones(), props.routingKeyNotificaciones(), reserva.getId());

        rabbitTemplate.convertAndSend(props.exchange(), props.routingKeyDocumentos(), payload,
                new CorrelationData("reserva-" + reserva.getId() + "-documento"));
        log.info("✅ Evento publicado -> {} (routingKey={}) para reserva #{}",
                props.queueDocumentos(), props.routingKeyDocumentos(), reserva.getId());

        // Exchange topic: el mismo evento tambien se publica por patron (lo recibe quien este enlazado a reserva.#).
        rabbitTemplate.convertAndSend(props.topicExchange(), props.routingKeyEventoReserva(), payload,
                new CorrelationData("reserva-" + reserva.getId() + "-evento"));
        log.info("📡 Evento publicado (topic) -> {} (routingKey={}) para reserva #{}",
                props.topicExchange(), props.routingKeyEventoReserva(), reserva.getId());

        publicarEnKafka(reserva);
    }

    /** Kafka (EP5): venta en tiempo real para el panel de reportes de ms-campuslab-reporteria. */
    private void publicarEnKafka(Reserva reserva) {
        VentaEventoDto evento = new VentaEventoDto(reserva.getId(), reserva.getNombre(), reserva.getRut(),
                String.valueOf(reserva.getFecha()), reserva.getProductoId(), reserva.getEstado().name());
        try {
            String json = objectMapper.writeValueAsString(evento);
            kafkaTemplate.send(kafkaProps.topicoVentas(), String.valueOf(reserva.getId()), json)
                    .whenComplete((resultado, error) -> {
                        if (error != null) {
                            log.error("❌ No se pudo publicar el evento de venta en Kafka para reserva #{}: {}",
                                    reserva.getId(), error.getMessage());
                        } else {
                            log.info("📈 Evento de venta publicado -> topico={} particion={} offset={} reserva #{}",
                                    kafkaProps.topicoVentas(), resultado.getRecordMetadata().partition(),
                                    resultado.getRecordMetadata().offset(), reserva.getId());
                        }
                    });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("❌ No se pudo serializar el evento de venta para reserva #{}: {}", reserva.getId(), e.getMessage());
        }
    }

    private String construirPayload(Reserva reserva) {
        return "Reserva #" + reserva.getId()
                + " | nombre=" + reserva.getNombre()
                + " | rut=" + reserva.getRut()
                + " | fecha=" + reserva.getFecha()
                + " | productoId=" + reserva.getProductoId()
                + " | estado=" + reserva.getEstado();
    }
}
