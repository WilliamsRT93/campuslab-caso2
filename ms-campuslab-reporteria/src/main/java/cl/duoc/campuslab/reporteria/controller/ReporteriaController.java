package cl.duoc.campuslab.reporteria.controller;

import cl.duoc.campuslab.reporteria.dto.PaginaEventoAuditoria;
import cl.duoc.campuslab.reporteria.dto.PaginaReporteVenta;
import cl.duoc.campuslab.reporteria.repository.EventoAuditoriaRepository;
import cl.duoc.campuslab.reporteria.repository.ReporteVentaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel de reportes en tiempo real (ventas) y registro de auditoria, alimentados SOLO por los
 * listeners Kafka -- esta API nunca escribe en las colecciones, solo las lee para mostrarlas.
 * Paginado por cursor ("before"): el front carga las primeras 50 y, mientras el usuario hace
 * scroll, pide paginas de 50 filas mas antiguas hasta llegar al "total" (scroll infinito).
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
    public ResponseEntity<PaginaReporteVenta> panelDeVentas(@RequestParam(required = false) Long before) {
        var items = before == null
                ? reporteVentaRepository.findTop50ByOrderByIdDesc()
                : reporteVentaRepository.findTop50ByIdLessThanOrderByIdDesc(before);
        return ResponseEntity.ok(new PaginaReporteVenta(reporteVentaRepository.count(), items));
    }

    @GetMapping("/auditoria")
    public ResponseEntity<PaginaEventoAuditoria> registroDeAuditoria(@RequestParam(required = false) Long before) {
        var items = before == null
                ? eventoAuditoriaRepository.findTop50ByOrderByIdDesc()
                : eventoAuditoriaRepository.findTop50ByIdLessThanOrderByIdDesc(before);
        return ResponseEntity.ok(new PaginaEventoAuditoria(eventoAuditoriaRepository.count(), items));
    }
}
