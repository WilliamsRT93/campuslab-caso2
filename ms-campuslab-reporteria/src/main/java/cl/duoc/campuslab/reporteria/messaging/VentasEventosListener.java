package cl.duoc.campuslab.reporteria.messaging;

import cl.duoc.campuslab.reporteria.domain.ReporteVenta;
import cl.duoc.campuslab.reporteria.dto.VentaEventoDto;
import cl.duoc.campuslab.reporteria.repository.ReporteVentaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * CONSUMIDOR del dominio ventas: SOLO lee el topico campuslab.ventas (productor real:
 * ms-campuslab-bookings). Nunca publica en este mismo topico -- regla de diseno de EP5
 * (productor y consumidor de un mismo flujo nunca son el mismo microservicio).
 * Alimenta el panel de reportes de ventas en tiempo real.
 */
@Component
public class VentasEventosListener {

    private static final Logger log = LoggerFactory.getLogger(VentasEventosListener.class);

    private final ReporteVentaRepository repository;
    private final ObjectMapper objectMapper;

    public VentasEventosListener(ReporteVentaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${campuslab.kafka.topics.ventas}", groupId = "${campuslab.kafka.consumer-groups.ventas}")
    public void escuchar(String payload) {
        VentaEventoDto evento = parsear(payload);
        guardar(evento);
        log.info("📊 Venta registrada en el panel -> reserva #{} estado={} producto #{}",
                evento.reservaId(), evento.estado(), evento.productoId());
    }

    /** Payload mal formado = error NO recuperable: no tiene sentido reintentar, va directo a la DLT. */
    private VentaEventoDto parsear(String payload) {
        try {
            return objectMapper.readValue(payload, VentaEventoDto.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Payload de venta invalido: " + ex.getMessage(), ex);
        }
    }

    /** Un fallo de BD es transitorio: se reintenta antes de ir a la DLT. */
    private void guardar(VentaEventoDto evento) {
        try {
            repository.save(new ReporteVenta(evento.reservaId(), evento.nombre(), evento.rut(),
                    evento.fecha(), evento.productoId(), evento.estado()));
        } catch (DataAccessException ex) {
            throw new ErrorRecuperableException("No se pudo guardar el reporte de venta (BD no disponible)", ex);
        }
    }
}
