package cl.duoc.campuslab.kafkaadmin.exception;

public class RecursoExistenteException extends RuntimeException {
    public RecursoExistenteException(String mensaje) {
        super(mensaje);
    }
}
