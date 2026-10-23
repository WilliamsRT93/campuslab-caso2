package cl.duoc.campuslab.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CrearExchangeRequest(

        @NotBlank(message = "El nombre del exchange no puede estar vacio")
        @Pattern(regexp = "^(?!amq\\.)[a-zA-Z0-9._-]{3,100}$",
                message = "El nombre debe tener 3-100 caracteres (letras, numeros, puntos, guiones y guion bajo) y no puede empezar con 'amq.' (prefijo reservado de RabbitMQ)")
        String nombre) {
}
