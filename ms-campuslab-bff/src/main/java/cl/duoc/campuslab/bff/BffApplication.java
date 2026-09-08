package cl.duoc.campuslab.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BFF (Backend For Frontend) de CampusLab.
 * Se ubica DETRAS del API Gateway y DELANTE de los microservicios de dominio.
 * Valida el access token JWT emitido por Amazon Cognito (federado con Entra ID),
 * aplica autorizacion por rol y reenvia la peticion al microservicio correspondiente.
 */
@SpringBootApplication
public class BffApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffApplication.class, args);
    }
}
