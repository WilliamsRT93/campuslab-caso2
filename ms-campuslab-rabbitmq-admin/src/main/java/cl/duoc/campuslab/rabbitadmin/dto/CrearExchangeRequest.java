package cl.duoc.campuslab.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CrearExchangeRequest(

        @NotBlank(message = "El nombre del exchange no puede estar vacio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,100}$",
                message = "El nombre solo puede tener letras, numeros, puntos, guiones y guion bajo (3-100 caracteres)")
        String nombre) {
}
