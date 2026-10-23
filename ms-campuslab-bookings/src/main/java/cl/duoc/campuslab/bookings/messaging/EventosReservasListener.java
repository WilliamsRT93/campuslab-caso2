package cl.duoc.campuslab.bookings.messaging;

import cl.duoc.campuslab.bookings.config.RabbitMessagingProperties;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * CONSUMIDOR del exchange TOPIC (dominio "reservas"). Recibe los eventos que coinciden con el patron reserva.#.
 * Recibe todo lo que el exchange topic enruta por patron a su cola y confirma con ACK manual; los errores se resuelven
 * con ManejadorErroresListener (reintento de los recuperables, DLQ para los no recuperables).
 */
@Service
public class EventosReservasListener {

    private static final Logger log = LoggerFactory.getLogger(EventosReservasListener.class);

    private final ManejadorErroresListener errores;
    private final RabbitMessagingProperties props;

    public EventosReservasListener(ManejadorErroresListener errores, RabbitMessagingProperties props) {
        this.errores = errores;
        this.props = props;
    }

    @RabbitListener(queues = "${campuslab.rabbitmq.queue-eventos-reservas}")
    public void recibir(String payload, Channel channel, @Header("amqp_deliveryTag") long deliveryTag,
                        @Header("amqp_receivedRoutingKey") String routingKey,
                        @Header(name = ManejadorErroresListener.HEADER_INTENTOS, required = false) Integer intentosPrevios) {
        int intentos = intentosPrevios == null ? 0 : intentosPrevios;
        try {
            if (payload == null || payload.isBlank() || payload.contains("FORZAR_FALLO")) {
                throw new IllegalArgumentException("Payload invalido para el evento de reserva");
            }
            errores.simularFalloTransitorio(payload, intentos);
            log.info("📡 Evento de reserva recibido (topic) (routingKey={}) -> {}", routingKey, payload);
            channel.basicAck(deliveryTag, false);
            log.info("✅ Evento de reserva confirmado (ACK)");
        } catch (Exception e) {
            errores.resolver(channel, deliveryTag, payload, intentos, props.topicExchange(), routingKey, "evento de reserva", e);
        }
    }
}
