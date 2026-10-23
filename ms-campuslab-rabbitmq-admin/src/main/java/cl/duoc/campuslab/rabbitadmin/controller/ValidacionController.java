package cl.duoc.campuslab.rabbitadmin.controller;

import cl.duoc.campuslab.rabbitadmin.dto.ValidacionColasResponse;
import cl.duoc.campuslab.rabbitadmin.dto.ValidacionRutasResponse;
import cl.duoc.campuslab.rabbitadmin.service.ValidacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Validadores para la demostracion, visibles en Swagger (solo rol Admin). */
@RestController
@RequestMapping("/api/rabbitmq/validacion")
@Tag(name = "Validacion", description = "Comprueba las rutas sin proxy del API Gateway y las colas con su listener")
public class ValidacionController {

    private final ValidacionService validacion;

    public ValidacionController(ValidacionService validacion) {
        this.validacion = validacion;
    }

    @Operation(summary = "Valida las rutas del API Gateway: JWT obligatorio, respuesta con token y ninguna ruta proxy")
    @GetMapping("/rutas")
    public ValidacionRutasResponse rutas(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return validacion.validarRutas(authorization);
    }

    @Operation(summary = "Valida cada cola: listener activo, binding, DLX, TTL y cola muerta (solo lectura)")
    @GetMapping("/colas")
    public ValidacionColasResponse colas() {
        return validacion.validarColas(false);
    }

    @Operation(summary = "Prueba extremo a extremo: publica un mensaje a cada cola y verifica que su listener lo consume sin enviarlo a la DLQ")
    @PostMapping("/colas/prueba")
    public ValidacionColasResponse pruebaColas() {
        return validacion.validarColas(true);
    }
}
