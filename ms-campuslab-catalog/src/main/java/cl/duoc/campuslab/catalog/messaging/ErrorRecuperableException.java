package cl.duoc.campuslab.catalog.messaging;

/** Error transitorio del procesamiento de un mensaje: vale la pena reintentar. */
public class ErrorRecuperableException extends RuntimeException {

    public ErrorRecuperableException(String mensaje) {
        super(mensaje);
    }
}
