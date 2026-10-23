package cl.duoc.campuslab.rabbitadmin.exception;

/** La cola o el exchange solicitado no existe en RabbitMQ (HTTP 404). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
