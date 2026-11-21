package cl.duoc.campuslab.auditoria.config;

import cl.duoc.campuslab.auditoria.messaging.ErrorRecuperableException;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Manejo de errores del lado consumidor (rubrica EP5, indicador "manejo adecuado de errores",
 * 12%): distingue error recuperable (reintenta con backoff fijo hasta maxReintentos) de no
 * recuperable (va directo al topico muerto "<topico>.dlt", equivalente Kafka de una DLQ).
 * Mismo patron que ms-campuslab-reporteria (ver KafkaConsumerConfig de ese modulo para el
 * detalle del bug real de AUTO_CREATE_TOPICS_ENABLE=false que motivo declarar el topico .dlt
 * como bean NewTopic).
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    NewTopic topicoAuditoriaDlt(KafkaTopicsProperties props) {
        return new NewTopic(props.topicoAuditoria() + ".dlt", 3, (short) 2);
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate, KafkaTopicsProperties props) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    log.error("➡️ Mensaje enviado a la DLT tras agotar reintentos. topico={} partition={} offset={} causa={}",
                            record.topic(), record.partition(), record.offset(), ex.getMessage());
                    return new org.apache.kafka.common.TopicPartition(record.topic() + ".dlt", record.partition());
                });

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(props.esperaReintentoMs(), props.maxReintentos()));

        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.addRetryableExceptions(ErrorRecuperableException.class);

        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("⚠️ Reintento {} para mensaje de {} (offset={}): {}",
                        deliveryAttempt, record.topic(), record.offset(), ex.getMessage()));

        return handler;
    }
}
