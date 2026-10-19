package cl.duoc.campuslab.catalog.messaging;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * CONSUMIDOR del dominio "auditoria". Registra en el log cada cambio del
 * catalogo. Mismo patron de ACK/NACK manual que los listeners de bookings:
 * "FORZAR_FALLO" en el payload simula un fallo real y dispara el envio a la
 * DLQ (cola-auditoria-dlq), usado por la demo de colas muertas del frontend.
 */
@Service
public class AuditoriaListener {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaListener.class);

    @RabbitListener(queues = "${campuslab.rabbitmq.queue-auditoria}")
    public void recibir(String payload, Channel channel, @Header("amqp_deliveryTag") long deliveryTag) {
        try {
            if (payload == null || payload.isBlank() || payload.contains("FORZAR_FALLO")) {
                throw new IllegalArgumentException("Payload invalido para registro de auditoria");
            }
            log.info("🗂️ Registrando auditoria -> {}", payload);
            channel.basicAck(deliveryTag, false);
            log.info("✅ Auditoria confirmada (ACK)");
        } catch (Exception e) {
            log.error("❌ Error registrando auditoria, enviando a DLQ: {}", e.getMessage());
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
