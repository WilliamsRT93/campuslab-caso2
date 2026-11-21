package cl.duoc.campuslab.notify.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de mensajeria pura -- SOLO declara el RabbitTemplate que usa
 * ManejadorErroresListener para republicar un mensaje cuando reintenta un error
 * recuperable. No conoce logica de negocio (mismo patron que bookings/catalog).
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        template.setConfirmCallback((correlation, ack, cause) -> {
            String id = correlation != null ? correlation.getId() : "sin-id";
            if (ack) {
                log.info("📨 Broker confirmó la recepción (publisher confirm ACK) -> {}", id);
            } else {
                log.error("❌ Broker NO confirmó el mensaje (publisher confirm NACK) -> {} causa={}", id, cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "↩️ Mensaje devuelto por el broker (sin cola destino): exchange={} routingKey={} motivo={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return template;
    }
}
