package cl.duoc.campuslab.catalog;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Carga productos de prueba al iniciar, para tener datos con que demostrar el CRUD. */
@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seed(ProductoRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            repository.save(new Producto("Laboratorio de Redes 3", "LABORATORIO", 1, true));
            repository.save(new Producto("Laboratorio de Electronica 1", "LABORATORIO", 1, true));
            repository.save(new Producto("Osciloscopio Rigol DS1054Z", "EQUIPO", 8, true));
            repository.save(new Producto("Kit Arduino Uno", "EQUIPO", 25, true));
            repository.save(new Producto("Cable de red Cat6 (metro)", "INSUMO", 300, true));
        };
    }
}
