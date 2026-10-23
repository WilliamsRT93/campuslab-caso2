package cl.duoc.campuslab.bookings.messaging;

import cl.duoc.campuslab.bookings.Reserva;
import cl.duoc.campuslab.bookings.config.RabbitMessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * PRODUCTOR de mensajes. Tras crear una reserva, publica dos eventos al
 * exchange principal: uno para que el worker de notificaciones avise al
 * usuario, y otro para que el worker de documentos genere el ticket/PDF.
 *
 * Esta clase NO conoce nombres de colas en crudo -- todo viene de
 * RabbitMessagingProperties (centralizado en application.yml).
 */
@Service
public class ReservaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReservaEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMessagingProperties props;

    public ReservaEventPublisher(RabbitTemplate rabbitTemplate, RabbitMessagingProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
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
