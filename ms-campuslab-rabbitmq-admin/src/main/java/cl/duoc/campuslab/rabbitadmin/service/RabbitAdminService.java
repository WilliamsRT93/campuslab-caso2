package cl.duoc.campuslab.rabbitadmin.service;

import cl.duoc.campuslab.rabbitadmin.config.RabbitMQProperties;
import cl.duoc.campuslab.rabbitadmin.exception.RecursoExistenteException;
import cl.duoc.campuslab.rabbitadmin.exception.RecursoNoEncontradoException;
import cl.duoc.campuslab.rabbitadmin.exception.SolicitudInvalidaException;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

/**
 * Toda la logica de creacion/eliminacion/consulta de colas, exchanges y bindings vive ACA, encapsulada. El
 * controller solo llama a metodos de alto nivel y no conoce detalles internos de RabbitMQ (rubrica EP3,
 * indicador "logica encapsulada en un servicio dedicado", 10%).
 *
 * Verifica contra el broker el resultado de cada operacion: RabbitAdmin puede ignorar un rechazo del broker
 * (por ejemplo un nombre con el prefijo reservado "amq."), y sin esta comprobacion la API respondia "creada"
 * aunque no existiera.
 */
@Service
public class RabbitAdminService {

    private static final Pattern NOMBRE = Pattern.compile("^[a-zA-Z0-9._-]{1,255}$");
    private static final String PREFIJO_RESERVADO = "amq.";

    private final RabbitAdmin rabbitAdmin;
    private final RabbitMQProperties props;

    public RabbitAdminService(RabbitAdmin rabbitAdmin, RabbitMQProperties props) {
        this.rabbitAdmin = rabbitAdmin;
        this.props = props;
    }

    public void crearCola(String nombre, boolean conDeadLetter) {
        validarNombre(nombre, "cola");
        rechazarReservado(nombre, "cola");
        if (existeCola(nombre)) {
            throw new RecursoExistenteException("La cola '" + nombre + "' ya existe");
        }
        if (conDeadLetter) {
            String dlq = nombre + props.dlqSuffix();
            rabbitAdmin.declareQueue(QueueBuilder.durable(dlq).build());
            rabbitAdmin.declareBinding(BindingBuilder.bind(new Queue(dlq)).to(new DirectExchange(props.dlx())).with(nombre));
            rabbitAdmin.declareQueue(QueueBuilder.durable(nombre)
                    .withArgument("x-dead-letter-exchange", props.dlx())
                    .withArgument("x-dead-letter-routing-key", nombre)
                    .build());
        } else {
            rabbitAdmin.declareQueue(QueueBuilder.durable(nombre).build());
        }
        if (!existeCola(nombre)) {
            throw new SolicitudInvalidaException("RabbitMQ no creo la cola '" + nombre
                    + "'. Revise el nombre: los que empiezan con '" + PREFIJO_RESERVADO + "' estan reservados.");
        }
    }

    public void eliminarCola(String nombre) {
        validarNombre(nombre, "cola");
        exigirCola(nombre);
        rabbitAdmin.deleteQueue(nombre);
    }

    public int purgarCola(String nombre) {
        validarNombre(nombre, "cola");
        exigirCola(nombre);
        return rabbitAdmin.purgeQueue(nombre);
    }

    /** Informacion de la cola; lanza RecursoNoEncontradoException si no existe. */
    public QueueInformation obtenerInfoCola(String nombre) {
        validarNombre(nombre, "cola");
        QueueInformation info = rabbitAdmin.getQueueInfo(nombre);
        if (info == null) {
            throw new RecursoNoEncontradoException("La cola '" + nombre + "' no existe");
        }
        return info;
    }

    public void crearExchange(String nombre) {
        validarNombre(nombre, "exchange");
        rechazarReservado(nombre, "exchange");
        if (existeExchange(nombre)) {
            throw new RecursoExistenteException("El exchange '" + nombre + "' ya existe");
        }
        rabbitAdmin.declareExchange(new DirectExchange(nombre, true, false));
        if (!existeExchange(nombre)) {
            throw new SolicitudInvalidaException("RabbitMQ no creo el exchange '" + nombre
                    + "'. Revise el nombre: los que empiezan con '" + PREFIJO_RESERVADO + "' estan reservados.");
        }
    }

    public void eliminarExchange(String nombre) {
        validarNombre(nombre, "exchange");
        rechazarReservado(nombre, "exchange");
        exigirExchange(nombre);
        rabbitAdmin.deleteExchange(nombre);
    }

    public void crearBinding(String cola, String exchange, String routingKey) {
        exigirCola(cola);
        exigirExchange(exchange);
        rabbitAdmin.declareBinding(BindingBuilder.bind(new Queue(cola)).to(new DirectExchange(exchange)).with(routingKey));
    }

    public void eliminarBinding(String cola, String exchange, String routingKey) {
        exigirCola(cola);
        exigirExchange(exchange);
        rabbitAdmin.removeBinding(BindingBuilder.bind(new Queue(cola)).to(new DirectExchange(exchange)).with(routingKey));
    }

    private boolean existeCola(String nombre) {
        return rabbitAdmin.getQueueInfo(nombre) != null;
    }

    private void exigirCola(String nombre) {
        if (!existeCola(nombre)) {
            throw new RecursoNoEncontradoException("La cola '" + nombre + "' no existe");
        }
    }

    private void exigirExchange(String nombre) {
        if (!existeExchange(nombre)) {
            throw new RecursoNoEncontradoException("El exchange '" + nombre + "' no existe");
        }
    }

    /** Consulta pasiva: no crea nada; si el exchange no existe el broker responde con error. */
    private boolean existeExchange(String nombre) {
        return Boolean.TRUE.equals(rabbitAdmin.getRabbitTemplate().execute(channel -> {
            try {
                channel.exchangeDeclarePassive(nombre);
                return true;
            } catch (IOException e) {
                return false;
            }
        }));
    }

    private void validarNombre(String nombre, String tipo) {
        if (nombre == null || !NOMBRE.matcher(nombre).matches()) {
            throw new SolicitudInvalidaException("El nombre del " + tipo
                    + " no es valido (solo letras, numeros, puntos, guiones y guion bajo; maximo 255 caracteres)");
        }
    }

    private void rechazarReservado(String nombre, String tipo) {
        if (nombre.startsWith(PREFIJO_RESERVADO)) {
            throw new SolicitudInvalidaException("El nombre del " + tipo + " no puede empezar con '"
                    + PREFIJO_RESERVADO + "' (prefijo reservado de RabbitMQ)");
        }
    }
}
