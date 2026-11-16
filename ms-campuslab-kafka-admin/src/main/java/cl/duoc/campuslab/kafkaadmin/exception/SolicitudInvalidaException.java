package cl.duoc.campuslab.kafkaadmin.exception;

public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
