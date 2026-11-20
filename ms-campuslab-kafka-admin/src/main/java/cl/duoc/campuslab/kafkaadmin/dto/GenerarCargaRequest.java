package cl.duoc.campuslab.kafkaadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Prueba de carga configurable (EP5): publica en burst "cantidadMensajes" eventos validos en el
 * topico logico elegido para forzar consumer lag visible mientras ms-campuslab-reporteria los
 * procesa. El tope de 2000 evita que el boton del frontend sature el cluster de demostracion.
 */
public record GenerarCargaRequest(

        @NotBlank(message = "El topico logico no puede estar vacio")
        @Pattern(regexp = "ventas|auditoria", message = "topicoLogico debe ser 'ventas' o 'auditoria'")
        String topicoLogico,

        @Min(value = 1, message = "cantidadMensajes debe ser al menos 1")
        @Max(value = 2000, message = "cantidadMensajes no puede superar 2000")
        int cantidadMensajes) {
}
