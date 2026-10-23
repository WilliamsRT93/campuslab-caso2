package cl.duoc.campuslab.rabbitadmin.controller;

import cl.duoc.campuslab.rabbitadmin.dto.PublicarMensajeRequest;
import cl.duoc.campuslab.rabbitadmin.dto.PublicarMensajeResponse;
import cl.duoc.campuslab.rabbitadmin.dto.TopologiaResponse;
import cl.duoc.campuslab.rabbitadmin.service.PublicadorPruebaService;
import cl.duoc.campuslab.rabbitadmin.service.TopologiaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints que usa el front para probar exchanges, bindings y colas (solo rol Admin). */
@RestController
@RequestMapping("/api/rabbitmq")
@Tag(name = "Prueba de mensajeria", description = "Topologia en vivo y publicacion de mensajes de prueba")
public class PruebaMensajeriaController {

    private final TopologiaService topologia;
    private final PublicadorPruebaService publicador;

    public PruebaMensajeriaController(TopologiaService topologia, PublicadorPruebaService publicador) {
        this.topologia = topologia;
        this.publicador = publicador;
    }

    @Operation(summary = "Topologia en vivo: exchanges, colas (mensajes, consumidores, TTL) y bindings")
    @GetMapping("/topologia")
    public TopologiaResponse topologia() {
        return topologia.obtener();
    }

    @Operation(summary = "Publicar un mensaje de prueba y ver la confirmacion del broker y a que cola llego")
    @PostMapping("/publicar")
    public PublicarMensajeResponse publicar(@Valid @RequestBody PublicarMensajeRequest request) {
        return publicador.publicar(request);
    }
}
