package cl.duoc.campuslab.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mensaje de prueba a publicar. Si no se indica exchange se usa el exchange principal. */
public record PublicarMensajeRequest(
        String exchange,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{1,80}$", message = "routingKey invalida (letras, numeros, . _ -)") String routingKey,
        @NotBlank @Size(max = 500) String mensaje) {}
