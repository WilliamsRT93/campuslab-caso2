package cl.duoc.campuslab.bookings.messaging;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * CONSUMIDOR del dominio "documentos". Simula la generacion del ticket/PDF
 * de la reserva. Mismo patron de ACK/NACK manual que NotificacionListener,
 * en una clase separada porque pertenece a un dominio funcional distinto
 * (rubrica EP3: consumidores agrupados por dominio).
 */
@Service
public class DocumentoListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentoListener.class);

    @RabbitListener(queues = "${campuslab.rabbitmq.queue-documentos}")
    public void recibir(String payload, Channel channel, @Header("amqp_deliveryTag") long deliveryTag) {
        try {
            if (payload == null || payload.isBlank() || payload.contains("FORZAR_FALLO")) {
                throw new IllegalArgumentException("Payload invalido para generar documento");
            }
            log.info("📄 Generando documento/ticket -> {}", payload);
            channel.basicAck(deliveryTag, false);
            log.info("✅ Documento generado y confirmado (ACK)");
        } catch (Exception e) {
            log.error("❌ Error generando documento, enviando a DLQ: {}", e.getMessage());
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
