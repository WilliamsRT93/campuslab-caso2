package cl.duoc.campuslab.catalog.messaging;

import cl.duoc.campuslab.catalog.Producto;
import cl.duoc.campuslab.catalog.config.KafkaMessagingProperties;
import cl.duoc.campuslab.catalog.config.RabbitMessagingProperties;
import cl.duoc.campuslab.catalog.dto.AuditoriaEventoDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * PRODUCTOR de eventos de auditoria del catalogo: toda creacion, actualizacion
 * o eliminacion de un recurso queda registrada como mensaje en
 * cola-auditoria, para trazabilidad (rubrica EP3: 3ra cola real en uso).
 * Ademas publica el mismo evento en Kafka (EP5), topico campuslab.auditoria, para que
 * ms-campuslab-reporteria lo registre en el panel de auditoria -- este microservicio SOLO
 * produce ese topico, nunca lo consume (regla de diseno: productor y consumidor nunca son
 * el mismo servicio).
 */
@Service
public class AuditoriaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMessagingProperties props;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaMessagingProperties kafkaProps;
    private final ObjectMapper objectMapper;

    public AuditoriaEventPublisher(RabbitTemplate rabbitTemplate, RabbitMessagingProperties props,
            KafkaTemplate<String, String> kafkaTemplate, KafkaMessagingProperties kafkaProps,
            ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProps = kafkaProps;
        this.objectMapper = objectMapper;
    }

    public void publicarEvento(String accion, Producto producto) {
        String payload = construirPayload(accion, producto);
        rabbitTemplate.convertAndSend(props.exchange(), props.routingKeyAuditoria(), payload,
                new CorrelationData("auditoria-" + accion + "-" + producto.getId()));
        log.info("✅ Evento publicado -> {} (routingKey={}) accion={} producto #{}",
                props.queueAuditoria(), props.routingKeyAuditoria(), accion, producto.getId());
        publicarEnTopic(accion, payload, producto.getId());
        publicarEnKafka(new AuditoriaEventoDto(accion, producto.getId(), producto.getNombre(),
                producto.getTipo(), producto.getStock(), producto.getDisponible()));
    }

    public void publicarEliminacion(String accion, Long productoId) {
        String payload = "Accion=" + accion + " | productoId=" + productoId;
        rabbitTemplate.convertAndSend(props.exchange(), props.routingKeyAuditoria(), payload,
                new CorrelationData("auditoria-" + accion + "-" + productoId));
        log.info("✅ Evento publicado -> {} (routingKey={}) accion={} producto #{}",
                props.queueAuditoria(), props.routingKeyAuditoria(), accion, productoId);
        publicarEnTopic(accion, payload, productoId);
        publicarEnKafka(new AuditoriaEventoDto(accion, productoId, null, null, null, null));
    }

    /** Kafka (EP5): evento de auditoria en tiempo real para ms-campuslab-reporteria. */
    private void publicarEnKafka(AuditoriaEventoDto evento) {
        try {
            String json = objectMapper.writeValueAsString(evento);
            kafkaTemplate.send(kafkaProps.topicoAuditoria(), String.valueOf(evento.productoId()), json)
                    .whenComplete((resultado, error) -> {
                        if (error != null) {
                            log.error("❌ No se pudo publicar el evento de auditoria en Kafka (producto #{}): {}",
                                    evento.productoId(), error.getMessage());
                        } else {
                            log.info("📈 Evento de auditoria publicado -> topico={} particion={} offset={} producto #{}",
                                    kafkaProps.topicoAuditoria(), resultado.getRecordMetadata().partition(),
                                    resultado.getRecordMetadata().offset(), evento.productoId());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("❌ No se pudo serializar el evento de auditoria (producto #{}): {}",
                    evento.productoId(), e.getMessage());
        }
    }

    /** Exchange topic: catalogo.producto.<accion> lo recibe quien este enlazado a catalogo.# */
    private void publicarEnTopic(String accion, String payload, Object id) {
        String routingKey = props.routingKeyEventoCatalogo() + "." + accion.toLowerCase();
        rabbitTemplate.convertAndSend(props.topicExchange(), routingKey, payload,
                new CorrelationData("evento-" + accion + "-" + id));
        log.info("📡 Evento publicado (topic) -> {} (routingKey={}) producto #{}", props.topicExchange(), routingKey, id);
    }

    private String construirPayload(String accion, Producto producto) {
        return "Accion=" + accion
                + " | productoId=" + producto.getId()
                + " | nombre=" + producto.getNombre()
                + " | tipo=" + producto.getTipo()
                + " | stock=" + producto.getStock()
                + " | disponible=" + producto.getDisponible();
    }
}
