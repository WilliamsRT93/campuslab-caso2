package cl.duoc.campuslab.bookings.messaging;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

/**
 * Decide que hacer cuando un consumidor falla, distinguiendo dos tipos de error:
 * <ul>
 *   <li><b>Recuperable</b> (transitorio: timeout, BD no disponible un instante): se reintenta. El mensaje
 *       se vuelve a publicar con la cabecera {@value #HEADER_INTENTOS} incrementada y se hace ACK del original.
 *       Tras {@code max-reintentos} intentos se envia a la DLQ.</li>
 *   <li><b>No recuperable</b> (el mensaje mismo es invalido, p. ej. payload vacio o FORZAR_FALLO): reintentar no
 *       sirve; NACK sin reencolar y RabbitMQ lo manda a la DLQ por el x-dead-letter-exchange.</li>
 * </ul>
 * En ambos casos el resultado queda registrado en el log.
 */
@Component
public class ManejadorErroresListener {

    public static final String HEADER_INTENTOS = "x-intentos";

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresListener.class);
    private static final Pattern FALLO_TRANSITORIO = Pattern.compile("FALLO_TRANSITORIO(?::(\\d+))?");

    private final RabbitTemplate rabbitTemplate;
    private final int maxReintentos;
    private final long esperaReintentoMs;

    public ManejadorErroresListener(
            RabbitTemplate rabbitTemplate,
            @Value("${campuslab.rabbitmq.max-reintentos:3}") int maxReintentos,
            @Value("${campuslab.rabbitmq.espera-reintento-ms:1000}") long esperaReintentoMs) {
        this.rabbitTemplate = rabbitTemplate;
        this.maxReintentos = maxReintentos;
        this.esperaReintentoMs = esperaReintentoMs;
    }

    /**
     * Demo de error recuperable: un payload con FALLO_TRANSITORIO[:n] falla en sus primeros n intentos
     * (2 por defecto) y despues se procesa bien. Con n mayor que max-reintentos agota los reintentos y va a la DLQ.
     */
    public void simularFalloTransitorio(String payload, int intentos) {
        if (payload == null) {
            return;
        }
        Matcher m = FALLO_TRANSITORIO.matcher(payload);
        if (m.find()) {
            int fallos = m.group(1) != null ? Integer.parseInt(m.group(1)) : 2;
            if (intentos < fallos) {
                throw new ErrorRecuperableException("Fallo transitorio simulado (intento " + (intentos + 1) + ")");
            }
        }
    }

    public boolean esRecuperable(Exception e) {
        return e instanceof ErrorRecuperableException
                || e instanceof TransientDataAccessException
                || e instanceof SocketTimeoutException;
    }

    /** Reintenta (si es recuperable y quedan intentos) o envia el mensaje a la DLQ. */
    public void resolver(Channel channel, long deliveryTag, String payload, int intentos,
                         String exchange, String routingKey, String contexto, Exception error) {
        boolean recuperable = esRecuperable(error);
        if (recuperable && intentos < maxReintentos) {
            int siguiente = intentos + 1;
            log.warn("🔁 Error recuperable en {} (intento {}/{}), se reintenta: {}",
                    contexto, siguiente, maxReintentos, error.getMessage());
            try {
                esperar(esperaReintentoMs * siguiente);
                rabbitTemplate.convertAndSend(exchange, routingKey, payload, mensaje -> {
                    mensaje.getMessageProperties().setHeader(HEADER_INTENTOS, siguiente);
                    mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return mensaje;
                });
                channel.basicAck(deliveryTag, false);
                return;
            } catch (Exception ex) {
                log.error("No se pudo reintentar el mensaje de {}: {}", contexto, ex.getMessage());
            }
        }
        String motivo = recuperable ? "reintentos agotados (" + maxReintentos + ")" : "error no recuperable";
        log.error("❌ Error en {} ({}), enviando a DLQ: {}", contexto, motivo, error.getMessage());
        nackSinReencolar(channel, deliveryTag);
    }

    private void nackSinReencolar(Channel channel, long deliveryTag) {
        try {
            channel.basicNack(deliveryTag, false, false);
        } catch (IOException ioException) {
            log.error("No se pudo hacer NACK del mensaje: {}", ioException.getMessage());
        }
    }

    private void esperar(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
