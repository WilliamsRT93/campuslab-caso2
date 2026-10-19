package cl.duoc.campuslab.bookings.messaging;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * CONSUMIDOR del dominio "notificaciones". Simula el envio de un correo/push
 * al usuario cuando se crea una reserva.
 *
 * Maneja la confirmacion de mensajes EXPLICITAMENTE (ACK manual): si el envio
 * "falla" (payload invalido, usado para la demo de colas muertas), se hace
 * NACK sin reencolar -- RabbitMQ lo manda automaticamente a su DLQ
 * (cola-notificaciones-dlq) gracias al x-dead-letter-exchange configurado
 * por el microservicio administrador.
 */
@Service
public class NotificacionListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionListener.class);

    @RabbitListener(queues = "${campuslab.rabbitmq.queue-notificaciones}")
    public void recibir(String payload, Channel channel, @Header("amqp_deliveryTag") long deliveryTag) {
        try {
            if (payload == null || payload.isBlank() || payload.contains("FORZAR_FALLO")) {
                throw new IllegalArgumentException("Payload invalido para notificacion");
            }
            log.info("📧 Enviando notificacion -> {}", payload);
            channel.basicAck(deliveryTag, false);
            log.info("✅ Notificacion confirmada (ACK)");
        } catch (Exception e) {
            log.error("❌ Error procesando notificacion, enviando a DLQ: {}", e.getMessage());
            nackSinReencolar(channel, deliveryTag);
        }
    }

    private void nackSinReencolar(Channel channel, long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, false);
        } catch (IOException ioException) {
            log.error("No se pudo hacer NACK del mensaje: {}", ioException.getMessage());
        }
    }
}
