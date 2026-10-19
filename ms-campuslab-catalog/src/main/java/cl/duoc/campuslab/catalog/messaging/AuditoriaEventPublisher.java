package cl.duoc.campuslab.catalog.messaging;

import cl.duoc.campuslab.catalog.Producto;
import cl.duoc.campuslab.catalog.config.RabbitMessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * PRODUCTOR de eventos de auditoria del catalogo: toda creacion, actualizacion
 * o eliminacion de un recurso queda registrada como mensaje en
 * cola-auditoria, para trazabilidad (rubrica EP3: 3ra cola real en uso).
 */
@Service
public class AuditoriaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMessagingProperties props;

    public AuditoriaEventPublisher(RabbitTemplate rabbitTemplate, RabbitMessagingProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    public void publicarEvento(String accion, Producto producto) {
        String payload = construirPayload(accion, producto);
        rabbitTemplate.convertAndSend(props.exchange(), props.routingKeyAuditoria(), payload);
        log.info("✅ Evento publicado -> {} (routingKey={}) accion={} producto #{}",
                props.queueAuditoria(), props.routingKeyAuditoria(), accion, producto.getId());
    }

    public void publicarEliminacion(String accion, Long productoId) {
        String payload = "Accion=" + accion + " | productoId=" + productoId;
        rabbitTemplate.convertAndSend(props.exchange(), props.routingKeyAuditoria(), payload);
        log.info("✅ Evento publicado -> {} (routingKey={}) accion={} producto #{}",
                props.queueAuditoria(), props.routingKeyAuditoria(), accion, productoId);
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
