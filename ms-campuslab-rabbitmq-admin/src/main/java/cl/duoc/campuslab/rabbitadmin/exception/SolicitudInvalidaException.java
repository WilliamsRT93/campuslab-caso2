package cl.duoc.campuslab.rabbitadmin.exception;

/** La solicitud es sintacticamente valida pero RabbitMQ no puede cumplirla (HTTP 400): nombre reservado, etc. */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
