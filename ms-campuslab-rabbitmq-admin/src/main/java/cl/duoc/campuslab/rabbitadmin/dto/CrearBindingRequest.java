package cl.duoc.campuslab.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Binding entre una cola y un exchange con una routing key; los tres campos se validan antes de llegar a RabbitMQ. */
public record CrearBindingRequest(

        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{1,100}$",
                message = "El nombre de la cola solo puede tener letras, numeros, puntos, guiones y guion bajo (hasta 100 caracteres)")
        String cola,

        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{1,100}$",
                message = "El nombre del exchange solo puede tener letras, numeros, puntos, guiones y guion bajo (hasta 100 caracteres)")
        String exchange,

        @NotBlank(message = "La routing key es obligatoria")
        @Pattern(regexp = "^[a-zA-Z0-9._*#-]{1,255}$",
                message = "La routing key solo puede tener letras, numeros, puntos, guiones, guion bajo, * y # (hasta 255 caracteres)")
        String routingKey) {
}
