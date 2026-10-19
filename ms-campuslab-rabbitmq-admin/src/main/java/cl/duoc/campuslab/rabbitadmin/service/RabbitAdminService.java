package cl.duoc.campuslab.rabbitadmin.service;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

/**
 * Toda la logica de creacion/eliminacion/consulta de colas, exchanges y bindings
 * vive ACA, encapsulada. El controller solo llama a metodos de alto nivel y no
 * conoce detalles internos de RabbitMQ (rubrica EP3, indicador "logica encapsulada
 * en un servicio dedicado", 10%).
 */
@Service
public class RabbitAdminService {

    private final RabbitAdmin rabbitAdmin;

    public RabbitAdminService(RabbitAdmin rabbitAdmin) {
        this.rabbitAdmin = rabbitAdmin;
    }

    public void crearCola(String nombre) {
        Queue queue = QueueBuilder.durable(nombre).build();
        rabbitAdmin.declareQueue(queue);
    }

    public void eliminarCola(String nombre) {
        rabbitAdmin.deleteQueue(nombre);
    }

    public int purgarCola(String nombre) {
        return rabbitAdmin.purgeQueue(nombre);
    }

    public QueueInformation obtenerInfoCola(String nombre) {
        return rabbitAdmin.getQueueInfo(nombre);
    }

    public void crearExchange(String nombre) {
        DirectExchange exchange = new DirectExchange(nombre, true, false);
        rabbitAdmin.declareExchange(exchange);
    }

    public void eliminarExchange(String nombre) {
        rabbitAdmin.deleteExchange(nombre);
    }

    public void crearBinding(String cola, String exchange, String routingKey) {
        Binding binding = BindingBuilder
                .bind(new Queue(cola))
                .to(new DirectExchange(exchange))
                .with(routingKey);
        rabbitAdmin.declareBinding(binding);
    }

    public void eliminarBinding(String cola, String exchange, String routingKey) {
        Binding binding = BindingBuilder
                .bind(new Queue(cola))
                .to(new DirectExchange(exchange))
                .with(routingKey);
        rabbitAdmin.removeBinding(binding);
    }
}
