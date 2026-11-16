package cl.duoc.campuslab.kafkaadmin.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de topicos y parametros de administracion (retencion, particiones, factor de
 * replicacion, cleanup policy), leidos desde application.yml (prefijo campuslab.kafka).
 * Ningun nombre ni parametro va escrito a mano en el codigo de negocio.
 */
@ConfigurationProperties(prefix = "campuslab.kafka")
public record KafkaAdminProperties(Map<String, TopicoConfig> topics, String dltSuffix) {

    public record TopicoConfig(
            String nombre,
            int particiones,
            int factorReplicacion,
            long retencionMs,
            String cleanupPolicy) {
    }

    public TopicoConfig configPara(String claveLogica) {
        return topics.get(claveLogica);
    }
}
