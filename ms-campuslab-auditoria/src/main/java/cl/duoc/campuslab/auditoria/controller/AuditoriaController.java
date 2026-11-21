package cl.duoc.campuslab.auditoria.controller;

import cl.duoc.campuslab.auditoria.dto.PaginaEventoAuditoria;
import cl.duoc.campuslab.auditoria.repository.EventoAuditoriaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro de auditoria, alimentado SOLO por AuditoriaEventosListener -- esta API nunca
 * escribe, solo lee para mostrarlo. Misma ruta que antes tenia ms-campuslab-reporteria
 * (/api/reporteria/auditoria) para no tener que tocar el frontend ni la ruta de API Gateway,
 * solo el microservicio de destino (ver infra/cloudformation/api-gateway-rutas.yaml,
 * parametro AuditoriaBaseUrl). Paginado por cursor ("before"), igual que el panel de ventas.
 */
@RestController
@RequestMapping("/api/reporteria/auditoria")
public class AuditoriaController {

    private final EventoAuditoriaRepository eventoAuditoriaRepository;

    public AuditoriaController(EventoAuditoriaRepository eventoAuditoriaRepository) {
        this.eventoAuditoriaRepository = eventoAuditoriaRepository;
    }

    @GetMapping
    public ResponseEntity<PaginaEventoAuditoria> registroDeAuditoria(@RequestParam(required = false) Long before) {
        var items = before == null
                ? eventoAuditoriaRepository.findTop50ByOrderByIdDesc()
                : eventoAuditoriaRepository.findTop50ByIdLessThanOrderByIdDesc(before);
        return ResponseEntity.ok(new PaginaEventoAuditoria(eventoAuditoriaRepository.count(), items));
    }
}
