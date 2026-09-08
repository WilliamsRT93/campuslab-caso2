package cl.duoc.campuslab.bff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prueba de arranque del BFF en perfil local (sin necesidad de un Cognito real).
 * La validacion JWT del perfil cognito se demuestra en vivo con Postman.
 */
@SpringBootTest
@ActiveProfiles("local")
class BffApplicationTests {

    @Test
    void contextLoads() {
    }
}
