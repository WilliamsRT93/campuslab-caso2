package cl.duoc.campuslab.reporteria.repository;

import cl.duoc.campuslab.reporteria.domain.EventoAuditoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, Long> {

    /** Primera pagina: los 50 eventos de auditoria mas recientes. */
    List<EventoAuditoria> findTop50ByOrderByIdDesc();

    /** Paginas siguientes del scroll infinito: los 50 eventos mas recientes ANTERIORES al cursor. */
    List<EventoAuditoria> findTop50ByIdLessThanOrderByIdDesc(Long id);
}
