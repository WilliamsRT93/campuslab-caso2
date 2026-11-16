package cl.duoc.campuslab.kafkaadmin.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Permite ajustar en caliente los parametros de administracion de un topico existente
 * (retencion y cleanup policy). Cambiar particiones/factor de replicacion de un topico
 * existente requiere operaciones distintas (solo se puede AUMENTAR particiones, nunca
 * reducir ni bajar el factor de replicacion) y se expone por separado.
 */
public record ActualizarConfigTopicoRequest(

        Long retencionMs,

        @Pattern(regexp = "delete|compact", message = "cleanupPolicy debe ser 'delete' o 'compact'")
        String cleanupPolicy) {

    public boolean vacio() {
        return retencionMs == null && (cleanupPolicy == null || cleanupPolicy.isBlank());
    }
}
