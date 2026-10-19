package cl.duoc.campuslab.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de mensajeria que este microservicio usa -- NO declara topologia,
 * solo referencia los mismos nombres centralizados en ms-campuslab-rabbitmq-admin.
 */
@ConfigurationProperties(prefix = "campuslab.rabbitmq")
public record RabbitMessagingProperties(
        String exchange,
        String queueAuditoria,
        String routingKeyAuditoria) {
}
