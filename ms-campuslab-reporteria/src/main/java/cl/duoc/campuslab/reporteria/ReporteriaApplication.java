package cl.duoc.campuslab.reporteria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReporteriaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReporteriaApplication.class, args);
    }
}
