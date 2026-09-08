package cl.duoc.campuslab.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de catalogo de CampusLab.
 * Expone el CRUD de productos/recursos (laboratorios, equipos e insumos).
 * Es un servicio interno: solo se consume desde el BFF, nunca directo desde el frontend.
 */
@SpringBootApplication
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
