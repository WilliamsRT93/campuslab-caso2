package cl.duoc.campuslab.notify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de exchange/colas/routing-keys leidos desde application.yml (prefijo campuslab.rabbitmq).
 * La topologia real (colas, DLQ, bindings) la declara ms-campuslab-rabbitmq-admin -- este
 * microservicio SOLO consume, nunca publica los mismos eventos que recibe (regla de diseno:
 * productor y consumidor nunca son el mismo servicio). Agrupa los nombres que antes estaban
 * duplicados en bookings (notificaciones/documentos/eventos-reservas) y catalog
 * (auditoria/eventos-catalogo), porque ahora un solo consumidor los atiende a todos.
 */
@ConfigurationProperties(prefix = "campuslab.rabbitmq")
public record RabbitMessagingProperties(
        String exchange,
        String queueNotificaciones,
        String queueDocumentos,
        String queueAuditoria,
        String routingKeyNotificaciones,
        String routingKeyDocumentos,
        String routingKeyAuditoria,
        String topicExchange,
        String queueEventosReservas,
        String queueEventosCatalogo,
        String routingKeyEventoReserva,
        String routingKeyEventoCatalogo) {
}
