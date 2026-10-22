package cl.duoc.campuslab.rabbitadmin.config;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sirve la pagina propia de Swagger (con el boton que captura el token de Cognito) en la
 * URL conocida /swagger-ui.html. Springdoc reserva el patron /swagger-ui*&#47;** para sus
 * archivos, que captura esa ruta; un controlador tiene prioridad sobre ese manejador.
 */
@Controller
@Hidden
public class SwaggerPaginaController {

    @GetMapping("/swagger-ui.html")
    public String pagina() {
        return "forward:/swagger.html";
    }
}
