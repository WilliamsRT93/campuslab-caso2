package cl.duoc.campuslab.kafkaadmin.controller;

import cl.duoc.campuslab.kafkaadmin.dto.ActualizarConfigTopicoRequest;
import cl.duoc.campuslab.kafkaadmin.dto.ConsumerGroupResponse;
import cl.duoc.campuslab.kafkaadmin.dto.CrearTopicoRequest;
import cl.duoc.campuslab.kafkaadmin.dto.TopicoResponse;
import cl.duoc.campuslab.kafkaadmin.service.KafkaAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST para administrar topicos y consumer groups de Kafka en caliente: crear, modificar,
 * eliminar e inspeccionar (rubrica EP5: microservicio administrador de Kafka). Documentada con
 * Swagger/OpenAPI, visible en /swagger-ui.html.
 */
@RestController
@RequestMapping("/api/kafka")
@Validated
@Tag(name = "Administracion Kafka", description = "Gestion dinamica de topicos y consumer groups")
public class KafkaAdminController {

    private final KafkaAdminService kafkaAdminService;

    public KafkaAdminController(KafkaAdminService kafkaAdminService) {
        this.kafkaAdminService = kafkaAdminService;
    }

    @Operation(summary = "Crear un topico nuevo")
    @PostMapping("/topics")
    public ResponseEntity<String> crearTopico(@Valid @RequestBody CrearTopicoRequest request) {
        kafkaAdminService.crearTopico(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Topico '" + request.nombre() + "' creado exitosamente (particiones=" + request.particiones()
                        + ", factorReplicacion=" + request.factorReplicacion() + ")");
    }

    @Operation(summary = "Listar todos los topicos con su configuracion")
    @GetMapping("/topics")
    public ResponseEntity<List<TopicoResponse>> listarTopicos() {
        return ResponseEntity.ok(kafkaAdminService.listarTopicos());
    }

    @Operation(summary = "Obtener informacion de un topico (particiones, replicas, retencion, cleanup policy)")
    @GetMapping("/topics/{nombre}")
    public ResponseEntity<TopicoResponse> obtenerTopico(@PathVariable String nombre) {
        return ResponseEntity.ok(kafkaAdminService.obtenerTopico(nombre));
    }

    @Operation(summary = "Eliminar un topico")
    @DeleteMapping("/topics/{nombre}")
    public ResponseEntity<String> eliminarTopico(@PathVariable String nombre) {
        kafkaAdminService.eliminarTopico(nombre);
        return ResponseEntity.ok("Topico '" + nombre + "' eliminado");
    }

    @Operation(summary = "Actualizar retencion y/o cleanup policy de un topico existente")
    @PatchMapping("/topics/{nombre}/config")
    public ResponseEntity<String> actualizarConfiguracion(@PathVariable String nombre,
            @Valid @RequestBody ActualizarConfigTopicoRequest request) {
        kafkaAdminService.actualizarConfiguracion(nombre, request);
        return ResponseEntity.ok("Configuracion del topico '" + nombre + "' actualizada");
    }

    @Operation(summary = "Aumentar el numero de particiones de un topico (Kafka no permite reducirlas)")
    @PatchMapping("/topics/{nombre}/particiones")
    public ResponseEntity<String> aumentarParticiones(@PathVariable String nombre,
            @RequestParam @Min(1) int nuevoTotal) {
        kafkaAdminService.aumentarParticiones(nombre, nuevoTotal);
        return ResponseEntity.ok("Topico '" + nombre + "' ahora tiene " + nuevoTotal + " particiones");
    }

    @Operation(summary = "Listar todos los consumer groups activos")
    @GetMapping("/consumer-groups")
    public ResponseEntity<List<ConsumerGroupResponse>> listarConsumerGroups() {
        return ResponseEntity.ok(kafkaAdminService.listarConsumerGroups());
    }

    @Operation(summary = "Inspeccionar un consumer group (miembros, topicos asignados, estado)")
    @GetMapping("/consumer-groups/{groupId}")
    public ResponseEntity<ConsumerGroupResponse> describirConsumerGroup(@PathVariable String groupId) {
        return ResponseEntity.ok(kafkaAdminService.describirConsumerGroup(groupId));
    }
}
