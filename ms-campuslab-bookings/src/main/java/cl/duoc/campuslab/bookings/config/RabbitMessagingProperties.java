package cl.duoc.campuslab.bookings.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de exchange/colas/routing-keys leidos desde application.yml
 * (prefijo campuslab.rabbitmq). La topologia real (colas, DLQ, bindings) la
 * declara ms-campuslab-rabbitmq-admin -- este microservicio solo publica y
 * consume, no redeclara infraestructura.
 */
@ConfigurationProperties(prefix = "campuslab.rabbitmq")
public record RabbitMessagingProperties(
        String exchange,
        String queueNotificaciones,
        String queueDocumentos,
        String routingKeyNotificaciones,
        String routingKeyDocumentos) {
}
