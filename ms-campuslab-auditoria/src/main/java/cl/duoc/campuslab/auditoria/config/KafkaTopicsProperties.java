package cl.duoc.campuslab.auditoria.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombre del topico y consumer group leidos desde application.yml (prefijo campuslab.kafka).
 * Ningun nombre va escrito a mano en las clases de negocio (listener).
 */
@ConfigurationProperties(prefix = "campuslab.kafka")
public record KafkaTopicsProperties(
        String topicoAuditoria,
        String consumerGroupAuditoria,
        int maxReintentos,
        long esperaReintentoMs) {
}
