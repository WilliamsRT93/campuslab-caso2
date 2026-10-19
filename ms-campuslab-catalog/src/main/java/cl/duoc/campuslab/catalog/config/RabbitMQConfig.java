package cl.duoc.campuslab.catalog.config;

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

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }
}
