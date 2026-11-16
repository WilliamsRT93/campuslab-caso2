package cl.duoc.campuslab.reporteria.controller;

import cl.duoc.campuslab.reporteria.domain.EventoAuditoria;
import cl.duoc.campuslab.reporteria.domain.ReporteVenta;
import cl.duoc.campuslab.reporteria.repository.EventoAuditoriaRepository;
import cl.duoc.campuslab.reporteria.repository.ReporteVentaRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel de reportes en tiempo real (ventas) y registro de auditoria, alimentados SOLO por los
 * listeners Kafka -- esta API nunca escribe en las colecciones, solo las lee para mostrarlas.
 */
@RestController
@RequestMapping("/api/reporteria")
public class ReporteriaController {

    private final ReporteVentaRepository reporteVentaRepository;
    private final EventoAuditoriaRepository eventoAuditoriaRepository;

    public ReporteriaController(ReporteVentaRepository reporteVentaRepository,
            EventoAuditoriaRepository eventoAuditoriaRepository) {
        this.reporteVentaRepository = reporteVentaRepository;
        this.eventoAuditoriaRepository = eventoAuditoriaRepository;
    }

    @GetMapping("/ventas")
    public ResponseEntity<List<ReporteVenta>> panelDeVentas() {
        return ResponseEntity.ok(reporteVentaRepository.findAll(Sort.by(Sort.Direction.DESC, "id")));
    }

    @GetMapping("/auditoria")
    public ResponseEntity<List<EventoAuditoria>> registroDeAuditoria() {
        return ResponseEntity.ok(eventoAuditoriaRepository.findAll(Sort.by(Sort.Direction.DESC, "id")));
    }
}
