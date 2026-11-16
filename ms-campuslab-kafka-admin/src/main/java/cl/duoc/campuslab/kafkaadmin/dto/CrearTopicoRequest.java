package cl.duoc.campuslab.kafkaadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO con validacion para evitar topicos con nombres vacios o configuraciones invalidas
 * (rubrica EP5: validacion de parametros de entrada).
 */
public record CrearTopicoRequest(

        @NotBlank(message = "El nombre del topico no puede estar vacio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{3,249}$",
                message = "El nombre debe tener 3-249 caracteres (letras, numeros, puntos, guiones y guion bajo)")
        String nombre,

        @Min(value = 1, message = "Debe haber al menos 1 particion")
        @Max(value = 50, message = "Maximo 50 particiones")
        int particiones,

        @Min(value = 1, message = "El factor de replicacion debe ser al menos 1")
        @Max(value = 10, message = "Factor de replicacion maximo 10")
        short factorReplicacion,

        Long retencionMs,

        @Pattern(regexp = "delete|compact", message = "cleanupPolicy debe ser 'delete' o 'compact'")
        String cleanupPolicy) {
}
