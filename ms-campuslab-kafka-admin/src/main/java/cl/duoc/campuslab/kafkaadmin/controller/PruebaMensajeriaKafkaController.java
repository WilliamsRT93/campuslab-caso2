package cl.duoc.campuslab.kafkaadmin.controller;

import cl.duoc.campuslab.kafkaadmin.dto.GenerarCargaRequest;
import cl.duoc.campuslab.kafkaadmin.dto.GenerarCargaResponse;
import cl.duoc.campuslab.kafkaadmin.dto.PublicarPruebaKafkaRequest;
import cl.duoc.campuslab.kafkaadmin.dto.PublicarPruebaKafkaResponse;
import cl.duoc.campuslab.kafkaadmin.service.PublicadorPruebaKafkaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Casos de prueba PREFABRICADOS, elegibles desde el boton "Try it out" de Swagger (dropdown de
 * ejemplos): publican un evento real en campuslab.ventas o campuslab.auditoria, valido o
 * deliberadamente malformado, para demostrar en vivo el flujo completo
 * Swagger -> Kafka -> ms-campuslab-reporteria (incluyendo el camino de error hacia la DLT).
 */
@RestController
@RequestMapping("/api/kafka/prueba")
@Tag(name = "Casos de prueba Kafka", description = "Publicar eventos de ejemplo (validos o con error) y ver el resultado en reporteria")
public class PruebaMensajeriaKafkaController {

    private final PublicadorPruebaKafkaService publicador;

    public PruebaMensajeriaKafkaController(PublicadorPruebaKafkaService publicador) {
        this.publicador = publicador;
    }

    @Operation(summary = "Publicar un caso de prueba prefabricado (venta/auditoria, valido/malformado)",
            description = "Elija uno de los 4 casos listos del dropdown de ejemplos. El caso VALIDO debe "
                    + "aparecer segundos despues en GET /api/reporteria/{ventas|auditoria}. El caso "
                    + "MALFORMADO debe terminar en el topico <nombre>.dlt sin generar una fila nueva.")
    @PostMapping(value = "/publicar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PublicarPruebaKafkaResponse publicar(
            @Valid @org.springframework.web.bind.annotation.RequestBody
            @RequestBody(content = @Content(examples = {
                    @ExampleObject(name = "Venta valida",
                            summary = "Publica una venta valida -> debe aparecer en el panel de ventas",
                            value = "{\"topicoLogico\": \"ventas\", \"tipoCaso\": \"VALIDO\"}"),
                    @ExampleObject(name = "Venta malformada",
                            summary = "Publica un payload invalido en el topico de ventas -> debe terminar en campuslab.ventas.dlt",
                            value = "{\"topicoLogico\": \"ventas\", \"tipoCaso\": \"MALFORMADO\"}"),
                    @ExampleObject(name = "Auditoria valida",
                            summary = "Publica un evento de auditoria valido -> debe aparecer en el panel de auditoria",
                            value = "{\"topicoLogico\": \"auditoria\", \"tipoCaso\": \"VALIDO\"}"),
                    @ExampleObject(name = "Auditoria malformada",
                            summary = "Publica un payload invalido en el topico de auditoria -> debe terminar en campuslab.auditoria.dlt",
                            value = "{\"topicoLogico\": \"auditoria\", \"tipoCaso\": \"MALFORMADO\"}")
            }))
            PublicarPruebaKafkaRequest request) {
        return publicador.publicar(request);
    }

    @Operation(summary = "Generar una carga configurable de mensajes para forzar consumer lag",
            description = "Publica 'cantidadMensajes' eventos validos EN BURST en el topico logico elegido "
                    + "(ventas o auditoria), sin esperar la confirmacion de cada uno antes de enviar el "
                    + "siguiente. Usado por el boton 'Generar carga' del panel de reporteria para que el "
                    + "consumer lag suba de forma visible y luego vuelva a 0 a medida que "
                    + "ms-campuslab-reporteria los procesa.")
    @PostMapping(value = "/carga", consumes = MediaType.APPLICATION_JSON_VALUE)
    public GenerarCargaResponse generarCarga(
            @Valid @org.springframework.web.bind.annotation.RequestBody
            @RequestBody(content = @Content(examples = {
                    @ExampleObject(name = "Carga de 200 mensajes en ventas",
                            value = "{\"topicoLogico\": \"ventas\", \"cantidadMensajes\": 200}"),
                    @ExampleObject(name = "Carga de 500 mensajes en auditoria",
                            value = "{\"topicoLogico\": \"auditoria\", \"cantidadMensajes\": 500}")
            }))
            GenerarCargaRequest request) {
        return publicador.generarCarga(request);
    }
}
