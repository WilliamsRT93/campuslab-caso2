package cl.duoc.campuslab.bff;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Enruta todas las llamadas /api/bookings/** hacia el microservicio de reservas. */
@RestController
public class BookingsProxyController {

    private final ForwardService forward;
    private final String bookingsUrl;

    public BookingsProxyController(ForwardService forward,
                                   @Value("${campuslab.downstream.bookings-url}") String bookingsUrl) {
        this.forward = forward;
        this.bookingsUrl = bookingsUrl;
    }

    @RequestMapping("/api/bookings/**")
    public ResponseEntity<String> proxy(HttpServletRequest request,
                                        @RequestBody(required = false) String body) {
        String query = request.getQueryString();
        String url = bookingsUrl + request.getRequestURI() + (query != null ? "?" + query : "");
        MediaType contentType = request.getContentType() != null
                ? MediaType.parseMediaType(request.getContentType()) : null;
        return forward.forward(HttpMethod.valueOf(request.getMethod()), url, body, contentType);
    }
}
