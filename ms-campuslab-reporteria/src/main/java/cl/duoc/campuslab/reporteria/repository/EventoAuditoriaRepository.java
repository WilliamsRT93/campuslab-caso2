package cl.duoc.campuslab.reporteria.repository;

import cl.duoc.campuslab.reporteria.domain.EventoAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, Long> {
}
