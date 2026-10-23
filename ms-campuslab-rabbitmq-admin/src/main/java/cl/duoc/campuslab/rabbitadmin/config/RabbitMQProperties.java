package cl.duoc.campuslab.rabbitadmin.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de colas, exchanges, DLX y routing keys, leidos desde application.yml
 * (prefijo campuslab.rabbitmq). Ningun nombre va escrito a mano en el codigo de negocio.
 */
@ConfigurationProperties(prefix = "campuslab.rabbitmq")
public record RabbitMQProperties(
        String exchange,
        String dlx,
        Map<String, String> queues,
        String dlqSuffix,
        Map<String, String> routingKeys,
        long messageTtlMs,
        Topic topic) {

    /** Exchange de tipo topic: enruta por patron (reserva.#, catalogo.#). queues y patterns comparten las claves. */
    public record Topic(String exchange, Map<String, String> queues, Map<String, String> patterns) {}

    public String dlqNameFor(String logicalQueueKey) {
        return queues.get(logicalQueueKey) + dlqSuffix;
    }
}
