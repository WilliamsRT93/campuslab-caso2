package cl.duoc.campuslab.reporteria.dto;

import cl.duoc.campuslab.reporteria.domain.EventoAuditoria;
import java.util.List;

/** Pagina de auditoria para scroll infinito -- mismo criterio que PaginaReporteVenta. */
public record PaginaEventoAuditoria(long total, List<EventoAuditoria> items) {
}
