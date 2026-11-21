package cl.duoc.campuslab.auditoria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AuditoriaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditoriaApplication.class, args);
    }
}
