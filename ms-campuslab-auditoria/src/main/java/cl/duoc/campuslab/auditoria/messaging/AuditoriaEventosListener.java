package cl.duoc.campuslab.auditoria.messaging;

import cl.duoc.campuslab.auditoria.domain.EventoAuditoria;
import cl.duoc.campuslab.auditoria.dto.AuditoriaEventoDto;
import cl.duoc.campuslab.auditoria.repository.EventoAuditoriaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * CONSUMIDOR del dominio auditoria: SOLO lee el topico campuslab.auditoria (productor real:
 * ms-campuslab-catalog). Nunca publica en este mismo topico -- regla de diseno de EP5
 * (productor y consumidor de un mismo flujo nunca son el mismo microservicio). Vive en su
 * propio microservicio, separado de ventas/reporteria (cada dominio, su propio consumidor).
 */
@Component
public class AuditoriaEventosListener {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaEventosListener.class);

    private final EventoAuditoriaRepository repository;
    private final ObjectMapper objectMapper;

    public AuditoriaEventosListener(EventoAuditoriaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${campuslab.kafka.topico-auditoria}", groupId = "${campuslab.kafka.consumer-group-auditoria}")
    public void escuchar(String payload) {
        AuditoriaEventoDto evento = parsear(payload);
        guardar(evento);
        log.info("📝 Auditoria registrada -> accion={} producto #{}", evento.accion(), evento.productoId());
    }

    private AuditoriaEventoDto parsear(String payload) {
        try {
            return objectMapper.readValue(payload, AuditoriaEventoDto.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Payload de auditoria invalido: " + ex.getMessage(), ex);
        }
    }

    private void guardar(AuditoriaEventoDto evento) {
        try {
            repository.save(new EventoAuditoria(evento.accion(), evento.productoId(), evento.nombre(),
                    evento.tipo(), evento.stock(), evento.disponible()));
        } catch (DataAccessException ex) {
            throw new ErrorRecuperableException("No se pudo guardar el evento de auditoria (BD no disponible)", ex);
        }
    }
}
