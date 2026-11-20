package cl.duoc.campuslab.reporteria.dto;

import cl.duoc.campuslab.reporteria.domain.ReporteVenta;
import java.util.List;

/**
 * Pagina de ventas para scroll infinito: "total" es el conteo real de filas en la base (para
 * que el front sepa cuantas le faltan por cargar), "items" son las 50 siguientes mas antiguas
 * que el cursor "before" (o las 50 mas recientes si no se envia cursor).
 */
public record PaginaReporteVenta(long total, List<ReporteVenta> items) {
}
