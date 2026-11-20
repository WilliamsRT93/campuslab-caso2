package cl.duoc.campuslab.reporteria.repository;

import cl.duoc.campuslab.reporteria.domain.ReporteVenta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReporteVentaRepository extends JpaRepository<ReporteVenta, Long> {

    /** Primera pagina: las 50 ventas mas recientes. */
    List<ReporteVenta> findTop50ByOrderByIdDesc();

    /** Paginas siguientes del scroll infinito: las 50 ventas mas recientes ANTERIORES al cursor. */
    List<ReporteVenta> findTop50ByIdLessThanOrderByIdDesc(Long id);
}
