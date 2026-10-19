package cl.duoc.campuslab.rabbitadmin.controller;

import cl.duoc.campuslab.rabbitadmin.dto.CrearBindingRequest;
import cl.duoc.campuslab.rabbitadmin.dto.CrearColaRequest;
import cl.duoc.campuslab.rabbitadmin.dto.CrearExchangeRequest;
import cl.duoc.campuslab.rabbitadmin.service.RabbitAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST para administrar la topologia de RabbitMQ en caliente: crear/eliminar
 * colas, exchanges y bindings. Documentada con Swagger/OpenAPI (visible en
 * /swagger-ui.html) -- sirve tambien como "dashboard para validar las APIs".
 */
@RestController
@RequestMapping("/api/rabbitmq")
@Tag(name = "Administracion RabbitMQ", description = "Gestion dinamica de colas, exchanges y bindings")
public class RabbitMQAdminController {

    private final RabbitAdminService resourceManager;

    public RabbitMQAdminController(RabbitAdminService resourceManager) {
        this.resourceManager = resourceManager;
    }

    @Operation(summary = "Crear una cola nueva")
    @PostMapping("/queues")
    public ResponseEntity<String> crearCola(@Valid @RequestBody CrearColaRequest request) {
        resourceManager.crearCola(request.nombre());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Cola '" + request.nombre() + "' creada exitosamente");
    }

    @Operation(summary = "Obtener informacion de una cola (mensajes, consumidores)")
    @GetMapping("/queues/{nombre}")
    public ResponseEntity<QueueInformation> obtenerCola(@PathVariable String nombre) {
        QueueInformation info = resourceManager.obtenerInfoCola(nombre);
        return info != null ? ResponseEntity.ok(info) : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Eliminar una cola")
    @DeleteMapping("/queues/{nombre}")
    public ResponseEntity<String> eliminarCola(@PathVariable String nombre) {
        resourceManager.eliminarCola(nombre);
        return ResponseEntity.ok("Cola '" + nombre + "' eliminada");
    }

    @Operation(summary = "Purgar (vaciar) una cola sin eliminarla")
    @PostMapping("/queues/{nombre}/purge")
    public ResponseEntity<String> purgarCola(@PathVariable String nombre) {
        int purgados = resourceManager.purgarCola(nombre);
        return ResponseEntity.ok("Cola '" + nombre + "' purgada, mensajes eliminados: " + purgados);
    }

    @Operation(summary = "Crear un exchange nuevo")
    @PostMapping("/exchanges")
    public ResponseEntity<String> crearExchange(@Valid @RequestBody CrearExchangeRequest request) {
        resourceManager.crearExchange(request.nombre());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Exchange '" + request.nombre() + "' creado exitosamente");
    }

    @Operation(summary = "Eliminar un exchange")
    @DeleteMapping("/exchanges/{nombre}")
    public ResponseEntity<String> eliminarExchange(@PathVariable String nombre) {
        resourceManager.eliminarExchange(nombre);
        return ResponseEntity.ok("Exchange '" + nombre + "' eliminado");
    }

    @Operation(summary = "Crear un binding entre una cola y un exchange")
    @PostMapping("/bindings")
    public ResponseEntity<String> crearBinding(@Valid @RequestBody CrearBindingRequest request) {
        resourceManager.crearBinding(request.cola(), request.exchange(), request.routingKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Binding creado: " + request.cola() + " <-> " + request.exchange()
                        + " (routingKey=" + request.routingKey() + ")");
    }

    @Operation(summary = "Eliminar un binding entre una cola y un exchange")
    @DeleteMapping("/bindings")
    public ResponseEntity<String> eliminarBinding(@Valid @RequestBody CrearBindingRequest request) {
        resourceManager.eliminarBinding(request.cola(), request.exchange(), request.routingKey());
        return ResponseEntity.ok("Binding eliminado: " + request.cola() + " <-> " + request.exchange());
    }
}
