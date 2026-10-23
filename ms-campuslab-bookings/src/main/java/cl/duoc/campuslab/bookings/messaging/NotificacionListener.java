package cl.duoc.campuslab.bookings.messaging;

import com.rabbitmq.client.Channel;
import cl.duoc.campuslab.bookings.config.RabbitMessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * CONSUMIDOR del dominio "notificaciones". Simula el envio de un correo/push al usuario cuando se crea una reserva.
 *
 * Confirma los mensajes EXPLICITAMENTE (ACK manual) y distingue dos tipos de error
 * (ver ManejadorErroresListener):
 *  - NO recuperable (payload invalido / FORZAR_FALLO): NACK sin reencolar y RabbitMQ lo manda a la DLQ.
 *  - Recuperable (p. ej. FALLO_TRANSITORIO): se reintenta con limite y, si se agotan, va a la DLQ.
 */
@Service
public class NotificacionListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionListener.class);

    private final ManejadorErroresListener errores;
    private final RabbitMessagingProperties props;

    public NotificacionListener(ManejadorErroresListener errores, RabbitMessagingProperties props) {
        this.errores = errores;
        this.props = props;
    }

    @RabbitListener(queues = "${campuslab.rabbitmq.queue-notificaciones}")
    public void recibir(String payload, Channel channel, @Header("amqp_deliveryTag") long deliveryTag,
                        @Header(name = ManejadorErroresListener.HEADER_INTENTOS, required = false) Integer intentosPrevios) {
        int intentos = intentosPrevios == null ? 0 : intentosPrevios;
        try {
            if (payload == null || payload.isBlank() || payload.contains("FORZAR_FALLO")) {
                throw new IllegalArgumentException("Payload invalido para notificacion");
            }
            errores.simularFalloTransitorio(payload, intentos);
            log.info("📧 Enviando notificacion -> {}", payload);
            channel.basicAck(deliveryTag, false);
            log.info("✅ Notificacion confirmada (ACK)");
        } catch (Exception e) {
            errores.resolver(channel, deliveryTag, payload, intentos, props.exchange(), props.routingKeyNotificaciones(), "notificacion", e);
        }
    }
}
