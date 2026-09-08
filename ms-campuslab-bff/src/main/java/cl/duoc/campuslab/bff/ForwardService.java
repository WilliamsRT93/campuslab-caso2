package cl.duoc.campuslab.bff;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Reenvia la peticion ya autorizada al microservicio de dominio y devuelve su respuesta tal cual
 * (mismo status y cuerpo). No lanza excepcion en 4xx/5xx: hace passthrough de los errores de dominio.
 */
@Service
public class ForwardService {

    private final RestClient client = RestClient.create();

    public ResponseEntity<String> forward(HttpMethod method, String url, String body, MediaType contentType) {
        RestClient.RequestBodySpec spec = client.method(method).uri(url);
        if (StringUtils.hasText(body)) {
            spec = spec.contentType(contentType != null ? contentType : MediaType.APPLICATION_JSON);
            spec = spec.body(body);
        }
        return spec.exchange((request, response) -> {
            byte[] bytes = response.getBody().readAllBytes();
            MediaType ct = response.getHeaders().getContentType();
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(ct != null ? ct : MediaType.APPLICATION_JSON)
                    .body(new String(bytes, StandardCharsets.UTF_8));
        });
    }
}
