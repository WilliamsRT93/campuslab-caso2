package cl.duoc.campuslab.rabbitadmin.dto;

import java.util.List;

/** Resultado de publicar: confirmacion del broker (publisher confirm) y si algun binding lo enruto. */
public record PublicarMensajeResponse(
        String exchange,
        String routingKey,
        boolean confirmado,
        boolean ruteado,
        List<String> colasDestino,
        String detalle) {}
