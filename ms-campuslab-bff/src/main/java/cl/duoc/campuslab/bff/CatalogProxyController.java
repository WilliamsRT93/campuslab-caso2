package cl.duoc.campuslab.bff;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Enruta todas las llamadas /api/catalog/** hacia el microservicio de catalogo. */
@RestController
public class CatalogProxyController {

    private final ForwardService forward;
    private final String catalogUrl;

    public CatalogProxyController(ForwardService forward,
                                  @Value("${campuslab.downstream.catalog-url}") String catalogUrl) {
        this.forward = forward;
        this.catalogUrl = catalogUrl;
    }

    @RequestMapping("/api/catalog/**")
    public ResponseEntity<String> proxy(HttpServletRequest request,
                                        @RequestBody(required = false) String body) {
        String query = request.getQueryString();
        String url = catalogUrl + request.getRequestURI() + (query != null ? "?" + query : "");
        MediaType contentType = request.getContentType() != null
                ? MediaType.parseMediaType(request.getContentType()) : null;
        return forward.forward(HttpMethod.valueOf(request.getMethod()), url, body, contentType);
    }
}
