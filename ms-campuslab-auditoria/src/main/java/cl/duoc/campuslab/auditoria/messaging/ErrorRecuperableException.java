package cl.duoc.campuslab.auditoria.messaging;

/** Fallos transitorios (ej. BD momentaneamente no disponible): se reintentan antes de ir a la DLT. */
public class ErrorRecuperableException extends RuntimeException {
    public ErrorRecuperableException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
