package cl.duoc.campuslab.bookings;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD de reservas con los cuatro verbos (GET, POST, PUT, DELETE)
 * mas el cambio de estado del ciclo de vida.
 */
@RestController
@RequestMapping("/api/bookings")
public class ReservaController {

    private final ReservaRepository repository;

    public ReservaController(ReservaRepository repository) {
        this.repository = repository;
    }

    /** GET: lista reservas, opcionalmente filtradas por estado. */
    @GetMapping
    public List<Reserva> listar(@RequestParam(required = false) EstadoReserva status) {
        return status == null ? repository.findAll() : repository.findByEstado(status);
    }

    /** GET: obtiene una reserva por id. */
    @GetMapping("/{id}")
    public ResponseEntity<Reserva> obtener(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST: crea una reserva. Nace en estado SOLICITADA. */
    @PostMapping
    public ResponseEntity<Reserva> crear(@Valid @RequestBody ReservaRequest request) {
        Reserva creada = repository.save(new Reserva(
                request.getNombre(), request.getRut(), request.getFecha(),
                request.getProductoId(), EstadoReserva.SOLICITADA));
        return ResponseEntity.created(URI.create("/api/bookings/" + creada.getId())).body(creada);
    }

    /** PUT: actualiza los datos de una reserva. */
    @PutMapping("/{id}")
    public ResponseEntity<Reserva> actualizar(@PathVariable Long id,
                                             @Valid @RequestBody ReservaRequest request) {
        return repository.findById(id)
                .map(reserva -> {
                    reserva.setNombre(request.getNombre());
                    reserva.setRut(request.getRut());
                    reserva.setFecha(request.getFecha());
                    reserva.setProductoId(request.getProductoId());
                    return ResponseEntity.ok(repository.save(reserva));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** PUT: cambia el estado. Regla: no se puede pasar a EN_USO sin haber APROBADO. */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> cambiarEstado(@PathVariable Long id,
                                           @Valid @RequestBody StatusRequest request) {
        return repository.findById(id)
                .<ResponseEntity<?>>map(reserva -> {
                    if (request.getStatus() == EstadoReserva.EN_USO
                            && reserva.getEstado() != EstadoReserva.APROBADA
                            && reserva.getEstado() != EstadoReserva.EN_PREPARACION) {
                        return ResponseEntity.badRequest().body(java.util.Map.of(
                                "status", 400,
                                "error", "Bad Request",
                                "message", "No se puede pasar a EN_USO sin haber APROBADO la reserva"));
                    }
                    reserva.setEstado(request.getStatus());
                    return ResponseEntity.ok(repository.save(reserva));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** DELETE: elimina una reserva. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
