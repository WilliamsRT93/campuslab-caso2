package cl.duoc.campuslab.kafkaadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Caso de prueba prefabricado: publica un evento de VENTA o AUDITORIA, valido o deliberadamente
 * malformado, para demostrar en el panel de Swagger el flujo completo Kafka -> ms-campuslab-reporteria
 * (incluyendo el camino de error -> DLT). "casosPrefabricados()" en el controller expone los 4
 * combinaciones listas para usar desde el boton "Try it out" de Swagger.
 */
public record PublicarPruebaKafkaRequest(

        @NotBlank(message = "El topico logico no puede estar vacio")
        @Pattern(regexp = "ventas|auditoria", message = "topicoLogico debe ser 'ventas' o 'auditoria'")
        String topicoLogico,

        @Pattern(regexp = "VALIDO|MALFORMADO", message = "tipoCaso debe ser 'VALIDO' o 'MALFORMADO'")
        String tipoCaso) {
}
