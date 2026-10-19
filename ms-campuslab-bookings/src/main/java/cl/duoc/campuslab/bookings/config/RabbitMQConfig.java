package cl.duoc.campuslab.bookings.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de mensajeria pura -- SOLO declara el RabbitTemplate que usa
 * el publicador. No conoce logica de negocio (rubrica EP3: separacion
 * logica de negocio / configuracion de mensajeria).
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
