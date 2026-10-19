package cl.duoc.campuslab.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO con validacion para evitar colas con nombres vacios o con caracteres
 * no permitidos por AMQP (rubrica EP3: validacion de parametros, 7%).
 */
public record CrearColaRequest(

        @NotBlank(message = "El nombre de la cola no puede estar vacio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,100}$",
                message = "El nombre solo puede tener letras, numeros, puntos, guiones y guion bajo (3-100 caracteres)")
        String nombre,

        boolean conDeadLetter) {
}
