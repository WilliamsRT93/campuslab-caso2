package cl.duoc.campuslab.rabbitadmin.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia completa de mensajeria: 1 exchange principal + 1 DLX, 3 colas de negocio
 * (notificaciones, documentos, auditoria) cada una con su DLQ, y sus bindings.
 *
 * Esta clase SOLO declara infraestructura de mensajeria -- no contiene logica de negocio
 * (rubrica EP3, indicador "el codigo no mezcla logica de negocio con configuracion de mensajeria").
 */
@Configuration
public class RabbitMQTopologyConfig {

    private final RabbitMQProperties props;

    public RabbitMQTopologyConfig(RabbitMQProperties props) {
        this.props = props;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public DirectExchange mainExchange() {
        return new DirectExchange(props.exchange(), true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(props.dlx(), true, false);
    }

    // --- Cola 1: notificaciones ---

    @Bean
    public Queue notificacionesQueue() {
        return buildQueueWithDlq("notificaciones");
    }

    @Bean
    public Queue notificacionesDlq() {
        return QueueBuilder.durable(props.dlqNameFor("notificaciones")).build();
    }

    @Bean
    public Binding notificacionesBinding() {
        return BindingBuilder.bind(notificacionesQueue()).to(mainExchange())
                .with(props.routingKeys().get("notificaciones"));
    }

    @Bean
    public Binding notificacionesDlqBinding() {
        return BindingBuilder.bind(notificacionesDlq()).to(deadLetterExchange())
                .with(props.routingKeys().get("notificaciones"));
    }

    // --- Cola 2: documentos ---

    @Bean
    public Queue documentosQueue() {
        return buildQueueWithDlq("documentos");
    }

    @Bean
    public Queue documentosDlq() {
        return QueueBuilder.durable(props.dlqNameFor("documentos")).build();
    }

    @Bean
    public Binding documentosBinding() {
        return BindingBuilder.bind(documentosQueue()).to(mainExchange())
                .with(props.routingKeys().get("documentos"));
    }

    @Bean
    public Binding documentosDlqBinding() {
        return BindingBuilder.bind(documentosDlq()).to(deadLetterExchange())
                .with(props.routingKeys().get("documentos"));
    }

    // --- Cola 3: auditoria ---

    @Bean
    public Queue auditoriaQueue() {
        return buildQueueWithDlq("auditoria");
    }

    @Bean
    public Queue auditoriaDlq() {
        return QueueBuilder.durable(props.dlqNameFor("auditoria")).build();
    }

    @Bean
    public Binding auditoriaBinding() {
        return BindingBuilder.bind(auditoriaQueue()).to(mainExchange())
                .with(props.routingKeys().get("auditoria"));
    }

    @Bean
    public Binding auditoriaDlqBinding() {
        return BindingBuilder.bind(auditoriaDlq()).to(deadLetterExchange())
                .with(props.routingKeys().get("auditoria"));
    }

    /**
     * Cola de negocio durable con argumentos de dead-lettering apuntando al DLX,
     * usando la misma routing key en la DLQ (patron 1 cola -> 1 DLQ).
     */
    private Queue buildQueueWithDlq(String logicalKey) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", props.dlx());
        args.put("x-dead-letter-routing-key", props.routingKeys().get(logicalKey));
        return QueueBuilder.durable(props.queues().get(logicalKey)).withArguments(args).build();
    }
}
