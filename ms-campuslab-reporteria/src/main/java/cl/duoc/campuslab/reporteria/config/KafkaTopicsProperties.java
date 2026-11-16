package cl.duoc.campuslab.reporteria.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de topicos y consumer groups leidos desde application.yml (prefijo campuslab.kafka).
 * Ningun nombre va escrito a mano en las clases de negocio (listeners).
 */
@ConfigurationProperties(prefix = "campuslab.kafka")
public record KafkaTopicsProperties(
        Map<String, String> topics,
        Map<String, String> consumerGroups,
        int maxReintentos,
        long esperaReintentoMs) {

    public String topicoVentas() {
        return topics.get("ventas");
    }

    public String topicoAuditoria() {
        return topics.get("auditoria");
    }
}
