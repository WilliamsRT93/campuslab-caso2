package cl.duoc.campuslab.catalog;

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
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD del catalogo de recursos. Cubre los cuatro verbos exigidos:
 * GET (listar / obtener), POST (crear), PUT (actualizar) y DELETE (eliminar).
 */
@RestController
@RequestMapping("/api/catalog/resources")
public class ProductoController {

    private final ProductoRepository repository;

    public ProductoController(ProductoRepository repository) {
        this.repository = repository;
    }

    /** GET: lista todos los recursos del catalogo. */
    @GetMapping
    public List<Producto> listar() {
        return repository.findAll();
    }

    /** GET: obtiene un recurso por id. */
    @GetMapping("/{id}")
    public ResponseEntity<Producto> obtener(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST: crea un recurso nuevo. */
    @PostMapping
    public ResponseEntity<Producto> crear(@Valid @RequestBody ProductoRequest request) {
        Producto creado = repository.save(new Producto(
                request.getNombre(), request.getTipo(), request.getStock(), request.getDisponible()));
        return ResponseEntity.created(URI.create("/api/catalog/resources/" + creado.getId())).body(creado);
    }

    /** PUT: actualiza cupo/stock y datos del recurso. */
    @PutMapping("/{id}")
    public ResponseEntity<Producto> actualizar(@PathVariable Long id,
                                               @Valid @RequestBody ProductoRequest request) {
        return repository.findById(id)
                .map(producto -> {
                    producto.setNombre(request.getNombre());
                    producto.setTipo(request.getTipo());
                    producto.setStock(request.getStock());
                    producto.setDisponible(request.getDisponible());
                    return ResponseEntity.ok(repository.save(producto));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** DELETE: elimina un recurso del catalogo. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
