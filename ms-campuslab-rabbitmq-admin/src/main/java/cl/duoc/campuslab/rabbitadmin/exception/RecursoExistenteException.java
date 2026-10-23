package cl.duoc.campuslab.rabbitadmin.exception;

/** La cola o el exchange que se intenta crear ya existe (HTTP 409). */
public class RecursoExistenteException extends RuntimeException {

    public RecursoExistenteException(String mensaje) {
        super(mensaje);
    }
}
