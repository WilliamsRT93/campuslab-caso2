package cl.duoc.campuslab.kafkaadmin.config;

import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Bean unico de AdminClient reutilizado por KafkaAdminService para toda operacion de gestion. */
@Configuration
public class KafkaAdminClientConfig {

    @Bean(destroyMethod = "close")
    AdminClient adminClient(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }
}
