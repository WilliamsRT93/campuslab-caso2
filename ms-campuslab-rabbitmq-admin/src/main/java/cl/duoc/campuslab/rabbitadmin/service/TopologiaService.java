package cl.duoc.campuslab.rabbitadmin.service;

import cl.duoc.campuslab.rabbitadmin.config.RabbitMQProperties;
import cl.duoc.campuslab.rabbitadmin.dto.TopologiaResponse;
import cl.duoc.campuslab.rabbitadmin.dto.TopologiaResponse.BindingInfo;
import cl.duoc.campuslab.rabbitadmin.dto.TopologiaResponse.ColaInfo;
import cl.duoc.campuslab.rabbitadmin.dto.TopologiaResponse.ExchangeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lee la topologia REAL desde la API de gestion de RabbitMQ (no es una lista fija):
 * exchanges propios, colas de negocio con sus DLQ (mensajes, consumidores, TTL) y bindings.
 * Solo expone los recursos de CampusLab declarados en application.yml.
 */
@Service
public class TopologiaService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final RabbitMQProperties props;
    private final String base;
    private final String autorizacion;

    public TopologiaService(
            RabbitMQProperties props,
            @Value("${campuslab.management-url:http://localhost:15672}") String base,
            @Value("${spring.rabbitmq.username}") String usuario,
            @Value("${spring.rabbitmq.password}") String clave) {
        this.props = props;
        this.base = base;
        this.autorizacion = "Basic " + Base64.getEncoder()
                .encodeToString((usuario + ":" + clave).getBytes(StandardCharsets.UTF_8));
    }

    public TopologiaResponse obtener() {
        Set<String> exchangesPropios = Set.of(props.exchange(), props.dlx());
        Set<String> colasNegocio = new HashSet<>(props.queues().values());
        Set<String> colasDlq = new HashSet<>();
        props.queues().keySet().forEach(k -> colasDlq.add(props.dlqNameFor(k)));

        List<ExchangeInfo> exchanges = new ArrayList<>();
        for (JsonNode e : leer("/api/exchanges/%2F")) {
            String nombre = e.path("name").asText();
            if (exchangesPropios.contains(nombre)) {
                String funcion = nombre.equals(props.dlx())
                        ? "Colas muertas: recibe lo que una cola rechaza o expira"
                        : "Principal: recibe lo que publican los microservicios";
                exchanges.add(new ExchangeInfo(nombre, e.path("type").asText(), e.path("durable").asBoolean(), funcion));
            }
        }

        List<ColaInfo> colas = new ArrayList<>();
        for (JsonNode q : leer("/api/queues/%2F")) {
            String nombre = q.path("name").asText();
            boolean negocio = colasNegocio.contains(nombre);
            if (!negocio && !colasDlq.contains(nombre)) {
                continue;
            }
            JsonNode args = q.path("arguments");
            colas.add(new ColaInfo(
                    nombre,
                    negocio ? "negocio" : "dlq",
                    q.path("messages").asLong(0),
                    q.path("consumers").asLong(0),
                    q.path("message_stats").path("publish").asLong(0),
                    q.path("message_stats").path("ack").asLong(0),
                    args.has("x-message-ttl") ? args.get("x-message-ttl").asLong() : null,
                    args.has("x-dead-letter-exchange") ? args.get("x-dead-letter-exchange").asText() : null,
                    q.path("node").asText(null)));
        }
        colas.sort((a, b) -> a.tipo().equals(b.tipo()) ? a.nombre().compareTo(b.nombre()) : a.tipo().compareTo(b.tipo()));

        List<BindingInfo> bindings = new ArrayList<>();
        for (JsonNode b : leer("/api/bindings/%2F")) {
            if (exchangesPropios.contains(b.path("source").asText()) && "queue".equals(b.path("destination_type").asText())) {
                bindings.add(new BindingInfo(b.path("source").asText(), b.path("routing_key").asText(), b.path("destination").asText()));
            }
        }
        bindings.sort((a, b) -> a.exchange().equals(b.exchange())
                ? a.routingKey().compareTo(b.routingKey())
                : a.exchange().compareTo(b.exchange()));

        return new TopologiaResponse(exchanges, colas, bindings,
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    /** Colas a las que un binding real enruta (exchange, routingKey). */
    public List<String> colasDestino(String exchange, String routingKey) {
        return obtener().bindings().stream()
                .filter(b -> b.exchange().equals(exchange) && b.routingKey().equals(routingKey))
                .map(BindingInfo::cola)
                .toList();
    }

    public Set<String> exchangesPermitidos() {
        return Set.of(props.exchange(), props.dlx());
    }

    private JsonNode leer(String ruta) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + ruta))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", autorizacion)
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "La API de gestion de RabbitMQ respondio " + res.statusCode());
            }
            return JSON.readTree(res.body());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Consulta a RabbitMQ interrumpida");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar la API de gestion de RabbitMQ: " + e.getMessage());
        }
    }
}
