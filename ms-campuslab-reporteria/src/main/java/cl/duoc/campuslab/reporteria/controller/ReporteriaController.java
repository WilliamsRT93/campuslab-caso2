package cl.duoc.campuslab.reporteria.controller;

import cl.duoc.campuslab.reporteria.dto.PaginaReporteVenta;
import cl.duoc.campuslab.reporteria.repository.ReporteVentaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel de ventas en tiempo real, alimentado SOLO por VentasEventosListener -- esta API nunca
 * escribe en la coleccion, solo la lee para mostrarla. Paginado por cursor ("before"): el front
 * carga las primeras 50 y, mientras el usuario hace scroll, pide paginas de 50 filas mas
 * antiguas hasta llegar al "total" (scroll infinito). El registro de auditoria vive en su
 * propio microservicio (ms-campuslab-auditoria) -- cada dominio, su propio consumidor.
 */
@RestController
@RequestMapping("/api/reporteria")
public class ReporteriaController {

    private final ReporteVentaRepository reporteVentaRepository;

    public ReporteriaController(ReporteVentaRepository reporteVentaRepository) {
        this.reporteVentaRepository = reporteVentaRepository;
    }

    @GetMapping("/ventas")
    public ResponseEntity<PaginaReporteVenta> panelDeVentas(@RequestParam(required = false) Long before) {
        var items = before == null
                ? reporteVentaRepository.findTop50ByOrderByIdDesc()
                : reporteVentaRepository.findTop50ByIdLessThanOrderByIdDesc(before);
        return ResponseEntity.ok(new PaginaReporteVenta(reporteVentaRepository.count(), items));
    }
}
