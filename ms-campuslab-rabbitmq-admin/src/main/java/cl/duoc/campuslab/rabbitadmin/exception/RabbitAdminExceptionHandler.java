package cl.duoc.campuslab.rabbitadmin.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Traduce los errores a respuestas JSON claras: {status, error, message} y, en validaciones,
 * el detalle por campo en "errores". Evita que un fallo de RabbitMQ termine en un 500 sin explicacion.
 */
@RestControllerAdvice
public class RabbitAdminExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errores.putIfAbsent(e.getField(), e.getDefaultMessage()));
        Map<String, Object> cuerpo = cuerpo(HttpStatus.BAD_REQUEST, "La validacion de la solicitud fallo");
        cuerpo.put("errores", errores);
        return ResponseEntity.badRequest().body(cuerpo);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> cuerpoIlegible(HttpMessageNotReadableException ex) {
        return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la peticion no es valido (JSON mal formado o tipos incorrectos)");
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<Map<String, Object>> solicitudInvalida(SolicitudInvalidaException ex) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado(RecursoNoEncontradoException ex) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RecursoExistenteException.class)
    public ResponseEntity<Map<String, Object>> existente(RecursoExistenteException ex) {
        return responder(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> conEstado(ResponseStatusException ex) {
        HttpStatus estado = HttpStatus.resolve(ex.getStatusCode().value());
        return responder(estado != null ? estado : HttpStatus.INTERNAL_SERVER_ERROR, ex.getReason());
    }

    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<Map<String, Object>> rabbit(AmqpException ex) {
        log.error("RabbitMQ rechazo la operacion: {}", ex.getMessage());
        return responder(HttpStatus.BAD_GATEWAY, "RabbitMQ rechazo la operacion: " + ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> responder(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(cuerpo(estado, mensaje));
    }

    private Map<String, Object> cuerpo(HttpStatus estado, String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", estado.value());
        cuerpo.put("error", estado.getReasonPhrase());
        cuerpo.put("message", mensaje);
        return cuerpo;
    }
}
