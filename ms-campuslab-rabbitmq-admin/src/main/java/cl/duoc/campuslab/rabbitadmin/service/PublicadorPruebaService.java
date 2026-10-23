package cl.duoc.campuslab.rabbitadmin.service;

import cl.duoc.campuslab.rabbitadmin.config.RabbitMQProperties;
import cl.duoc.campuslab.rabbitadmin.dto.PublicarMensajeRequest;
import cl.duoc.campuslab.rabbitadmin.dto.PublicarMensajeResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Publica un mensaje de prueba y espera la respuesta del broker: el publisher confirm (ACK/NACK)
 * y, con mandatory, si ningun binding lo enruto (el broker lo devuelve).
 */
@Service
public class PublicadorPruebaService {

    private static final long ESPERA_CONFIRM_SEGUNDOS = 5;

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties props;
    private final TopologiaService topologia;

    public PublicadorPruebaService(RabbitTemplate rabbitTemplate, RabbitMQProperties props, TopologiaService topologia) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
        this.topologia = topologia;
    }

    public PublicarMensajeResponse publicar(PublicarMensajeRequest req) {
        String exchange = (req.exchange() == null || req.exchange().isBlank()) ? props.exchange() : req.exchange();
        if (!topologia.exchangesPermitidos().contains(exchange)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Exchange no permitido. Use " + props.exchange() + " o " + props.dlx());
        }
        CorrelationData correlacion = new CorrelationData("prueba-front-" + UUID.randomUUID());
        try {
            rabbitTemplate.convertAndSend(exchange, req.routingKey(), req.mensaje(), m -> {
                m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return m;
            }, correlacion);
            CorrelationData.Confirm confirm = correlacion.getFuture().get(ESPERA_CONFIRM_SEGUNDOS, TimeUnit.SECONDS);
            boolean confirmado = confirm.isAck();
            boolean ruteado = confirmado && correlacion.getReturned() == null;
            List<String> destino = ruteado ? topologia.colasDestino(exchange, req.routingKey()) : List.of();
            String detalle;
            if (!confirmado) {
                detalle = "El broker NO confirmo el mensaje (NACK): " + confirm.getReason();
            } else if (!ruteado) {
                detalle = "El broker confirmo la recepcion, pero ningun binding coincide con la routing key '"
                        + req.routingKey() + "' en " + exchange + ": el mensaje fue devuelto (sin cola destino).";
            } else {
                detalle = "El broker confirmo el mensaje (publisher confirm ACK) y lo enruto a: " + String.join(", ", destino) + ".";
            }
            return new PublicarMensajeResponse(exchange, req.routingKey(), confirmado, ruteado, destino, detalle);
        } catch (TimeoutException e) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                    "El broker no confirmo el mensaje en " + ESPERA_CONFIRM_SEGUNDOS + " s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Publicacion interrumpida");
        } catch (java.util.concurrent.ExecutionException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error al publicar: " + e.getCause());
        }
    }
}
