package cl.duoc.campuslab.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombre del topico Kafka de auditoria leido desde application.yml (prefijo campuslab.kafka).
 * El topico en si (particiones, replicacion, retencion) lo declara/administra
 * ms-campuslab-kafka-admin -- este microservicio solo publica, nunca lo redeclara ni lo consume.
 */
@ConfigurationProperties(prefix = "campuslab.kafka")
public record KafkaMessagingProperties(String topicoAuditoria) {
}
