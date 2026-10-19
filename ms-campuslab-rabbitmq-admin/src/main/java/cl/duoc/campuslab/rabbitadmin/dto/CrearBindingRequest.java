package cl.duoc.campuslab.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;

public record CrearBindingRequest(
        @NotBlank(message = "El nombre de la cola es obligatorio") String cola,
        @NotBlank(message = "El nombre del exchange es obligatorio") String exchange,
        @NotBlank(message = "La routing key es obligatoria") String routingKey) {
}
