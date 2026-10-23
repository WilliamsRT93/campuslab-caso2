package cl.duoc.campuslab.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO con validacion para evitar colas con nombres vacios, con caracteres no permitidos por AMQP o con el
 * prefijo reservado "amq." (rubrica EP3: validacion de parametros, 7%).
 * Si conDeadLetter es true se crea tambien la cola muerta "<nombre>-dlq" enlazada al DLX.
 */
public record CrearColaRequest(

        @NotBlank(message = "El nombre de la cola no puede estar vacio")
        @Pattern(regexp = "^(?!amq\\.)[a-zA-Z0-9._-]{3,100}$",
                message = "El nombre debe tener 3-100 caracteres (letras, numeros, puntos, guiones y guion bajo) y no puede empezar con 'amq.' (prefijo reservado de RabbitMQ)")
        String nombre,

        boolean conDeadLetter) {
}
