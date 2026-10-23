package cl.duoc.campuslab.catalog.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de mensajeria pura -- SOLO declara el RabbitTemplate que usa
 * el publicador. La topologia (colas/exchanges/bindings) la posee
 * ms-campuslab-rabbitmq-admin.
 */
@Configuration
@ConfigurationPropertiesScan
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    /**
     * Publisher confirms: el broker confirma (ACK) o rechaza (NACK) cada mensaje publicado.
     * Publisher returns: el broker devuelve el mensaje si ninguna cola lo recibio (mandatory).
     * Requiere spring.rabbitmq.publisher-confirm-type=correlated y publisher-returns=true.
     */
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
