package cl.duoc.campuslab.reporteria.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de topicos y consumer groups leidos desde application.yml (prefijo campuslab.kafka).
 * Ningun nombre va escrito a mano en las clases de negocio (listener). Este microservicio
 * solo consume el dominio ventas -- auditoria vive en ms-campuslab-auditoria.
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
}
