package cl.duoc.campuslab.reporteria.config;

import cl.duoc.campuslab.reporteria.messaging.ErrorRecuperableException;
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
 * El offset SOLO avanza cuando el listener termina sin excepcion o cuando el recoverer ya
 * publico en la DLT -- nunca se pierde un mensaje en silencio.
 *
 * IMPORTANTE (bug real encontrado probando en vivo contra el cluster con AUTO_CREATE_TOPICS_ENABLE=false):
 * si el topico ".dlt" no existe, el productor del recoverer reintenta para siempre pidiendo
 * metadata ("UNKNOWN_TOPIC_OR_PARTITION") y BLOQUEA el hilo consumidor completo -- ni siquiera
 * los mensajes SIGUIENTES, validos, se procesan. Por eso los topicos DLT se declaran aca como
 * beans NewTopic: el KafkaAdmin autoconfigurado por Spring Boot los crea solo al arrancar,
 * sin depender de que alguien los haya creado antes a mano en ms-campuslab-kafka-admin.
 * Mismo numero de particiones que los topicos de negocio (3): si el DLT tuviera menos
 * particiones, reenviar un mensaje de la particion 1 o 2 fallaria igual.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    NewTopic topicoVentasDlt(KafkaTopicsProperties props) {
        return new NewTopic(props.topicoVentas() + ".dlt", 3, (short) 2);
    }

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

        // Los errores de parseo/validacion del payload son NO recuperables: directo a la DLT, sin reintentar.
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        // ErrorRecuperableException (ej. fallo transitorio de BD) SI se reintenta.
        handler.addRetryableExceptions(ErrorRecuperableException.class);

        handler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("⚠️ Reintento {} para mensaje de {} (offset={}): {}",
                        deliveryAttempt, record.topic(), record.offset(), ex.getMessage()));

        return handler;
    }
}
