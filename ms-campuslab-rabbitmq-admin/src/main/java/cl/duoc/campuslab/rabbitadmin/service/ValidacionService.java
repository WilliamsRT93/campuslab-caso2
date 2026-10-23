package cl.duoc.campuslab.rabbitadmin.service;

import cl.duoc.campuslab.rabbitadmin.config.RabbitMQProperties;
import cl.duoc.campuslab.rabbitadmin.config.ValidacionProperties;
import cl.duoc.campuslab.rabbitadmin.dto.PublicarMensajeRequest;
import cl.duoc.campuslab.rabbitadmin.dto.PublicarMensajeResponse;
import cl.duoc.campuslab.rabbitadmin.dto.TopologiaResponse;
import cl.duoc.campuslab.rabbitadmin.dto.TopologiaResponse.ColaInfo;
import cl.duoc.campuslab.rabbitadmin.dto.ValidacionColasResponse;
import cl.duoc.campuslab.rabbitadmin.dto.ValidacionColasResponse.ColaValidada;
import cl.duoc.campuslab.rabbitadmin.dto.ValidacionColasResponse.Prueba;
import cl.duoc.campuslab.rabbitadmin.dto.ValidacionRutasResponse;
import cl.duoc.campuslab.rabbitadmin.dto.ValidacionRutasResponse.ControlResultado;
import cl.duoc.campuslab.rabbitadmin.dto.ValidacionRutasResponse.RutaResultado;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.stereotype.Service;

/**
 * Validadores para la demostracion:
 *  - rutas: llama al API Gateway desde fuera y comprueba que las rutas declaradas exigen JWT (401 sin token) y
 *    responden con token, y que las rutas NO declaradas devuelven 404 (es decir, no hay proxy generico);
 *  - colas: comprueba que cada cola de negocio tiene su listener activo, su binding, su DLX/TTL y su cola muerta,
 *    y opcionalmente publica un mensaje real y verifica que el listener lo consume sin enviarlo a la DLQ.
 */
@Service
public class ValidacionService {

    private static final long ESPERA_CONSUMO_MS = 8000;

    private final TopologiaService topologia;
    private final PublicadorPruebaService publicador;
    private final RabbitAdminService admin;
    private final RabbitMQProperties props;
    private final ValidacionProperties validacion;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public ValidacionService(TopologiaService topologia, PublicadorPruebaService publicador, RabbitAdminService admin,
                             RabbitMQProperties props, ValidacionProperties validacion) {
        this.topologia = topologia;
        this.publicador = publicador;
        this.admin = admin;
        this.props = props;
        this.validacion = validacion;
    }

    // ------------------------------------------------------------------ rutas del API Gateway

    public ValidacionRutasResponse validarRutas(String authorization) {
        List<RutaResultado> rutas = new ArrayList<>();
        for (var r : validacion.rutas()) {
            Integer sin = llamar(r.metodo(), r.ruta(), r.cuerpo(), null);
            Integer con = authorization == null ? null : llamar(r.metodo(), r.ruta(), r.cuerpo(), authorization);
            boolean ok = Integer.valueOf(401).equals(sin) && con != null && con == r.esperado();
            rutas.add(new RutaResultado(r.metodo(), r.ruta(), r.esperado(), sin, con, ok));
        }
        List<ControlResultado> controles = new ArrayList<>();
        for (var c : validacion.controles()) {
            Integer sin = llamar(c.metodo(), c.ruta(), null, null);
            Integer con = authorization == null ? null : llamar(c.metodo(), c.ruta(), null, authorization);
            boolean ok = Integer.valueOf(404).equals(sin) && (authorization == null || Integer.valueOf(404).equals(con));
            controles.add(new ControlResultado(c.metodo(), c.ruta(), sin, con, ok));
        }
        int correctas = (int) rutas.stream().filter(RutaResultado::ok).count();
        boolean protegidas = rutas.stream().allMatch(r -> Integer.valueOf(401).equals(r.sinToken()));
        boolean sinProxy = !controles.isEmpty() && controles.stream().allMatch(ControlResultado::ok);
        boolean ok = correctas == rutas.size() && protegidas && sinProxy;
        String conclusion = ok
                ? "OK: las " + rutas.size() + " rutas declaradas exigen JWT y responden; las " + controles.size()
                  + " rutas no declaradas devuelven 404, por lo tanto no hay proxy generico."
                : "REVISAR: " + (rutas.size() - correctas) + " ruta(s) declarada(s) con respuesta inesperada"
                  + (sinProxy ? "" : " y alguna ruta no declarada NO devolvio 404 (posible proxy)") + ".";
        return new ValidacionRutasResponse(validacion.apiGatewayUrl(), rutas.size(), correctas, protegidas, sinProxy, ok,
                conclusion, rutas, controles);
    }

    /** Devuelve el codigo HTTP, o null si no hubo respuesta. */
    private Integer llamar(String metodo, String ruta, String cuerpo, String authorization) {
        try {
            HttpRequest.BodyPublisher body = cuerpo == null || cuerpo.isBlank()
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(cuerpo);
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(validacion.apiGatewayUrl() + ruta))
                    .timeout(Duration.ofSeconds(validacion.timeoutSegundos()))
                    .method(metodo, body);
            if (cuerpo != null && !cuerpo.isBlank()) {
                req.header("Content-Type", "application/json");
            }
            if (authorization != null) {
                req.header("Authorization", authorization);
            }
            return http.send(req.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ colas con listener

    public ValidacionColasResponse validarColas(boolean probar) {
        TopologiaResponse topo = topologia.obtener();
        List<ColaValidada> resultado = new ArrayList<>();
        for (String clave : props.queues().keySet()) {
            String rk = props.routingKeys().get(clave);
            // cola de negocio del exchange direct: la routing key exacta, tambien en el DLX
            resultado.add(validarCola(topo, props.queues().get(clave), props.exchange(), rk, rk, rk, probar));
        }
        if (props.topic() != null) {
            for (String clave : props.topic().queues().keySet()) {
                String cola = props.topic().queues().get(clave);
                String patron = props.topic().patterns().get(clave);
                // cola del exchange topic: binding por patron; en el DLX (direct) los muertos van con el nombre de la cola
                resultado.add(validarCola(topo, cola, props.topic().exchange(), patron, cola,
                        patron.replace("#", "validacion").replace("*", "validacion"), probar));
            }
        }
        boolean ok = resultado.stream().allMatch(ColaValidada::ok);
        String conclusion = ok
                ? "OK: " + resultado.size() + " colas con listener activo, binding, DLX, TTL y cola muerta"
                  + (probar ? "; los mensajes de prueba fueron consumidos sin pasar a la DLQ." : ".")
                : "REVISAR: alguna cola no cumple (ver el detalle por cola).";
        return new ValidacionColasResponse(ok, probar, conclusion, resultado);
    }

    private ColaValidada validarCola(TopologiaResponse topo, String nombre, String exchange, String bindingKey,
                                     String dlqRoutingKey, String claveDePrueba, boolean probar) {
        String dlq = nombre + props.dlqSuffix();
        ColaInfo cola = topo.colas().stream().filter(c -> c.nombre().equals(nombre)).findFirst().orElse(null);
        boolean existe = cola != null;
        long consumidores = existe ? cola.consumidores() : 0;
        boolean binding = topo.bindings().stream().anyMatch(b -> b.exchange().equals(exchange)
                && b.routingKey().equals(bindingKey) && b.cola().equals(nombre));
        boolean tieneDlx = existe && props.dlx().equals(cola.dlx());
        boolean tieneTtl = existe && cola.ttlMs() != null;
        boolean dlqExiste = topo.colas().stream().anyMatch(c -> c.nombre().equals(dlq));
        boolean dlqBinding = topo.bindings().stream().anyMatch(b -> b.exchange().equals(props.dlx())
                && b.routingKey().equals(dlqRoutingKey) && b.cola().equals(dlq));
        boolean configOk = existe && consumidores >= 1 && binding && tieneDlx && tieneTtl && dlqExiste && dlqBinding;

        Prueba prueba = null;
        if (probar && configOk) {
            prueba = probarCola(nombre, exchange, claveDePrueba, dlq);
        }
        boolean ok = configOk && (!probar || (prueba != null && prueba.consumida() && !prueba.enviadaADlq()));
        return new ColaValidada(nombre, exchange, bindingKey, existe, consumidores, consumidores >= 1, binding, tieneDlx,
                tieneTtl, dlq, dlqExiste && dlqBinding, configOk, prueba, ok);
    }

    private Prueba probarCola(String cola, String exchange, String routingKey, String dlq) {
        long dlqAntes = contar(dlq);
        PublicarMensajeResponse pub;
        try {
            pub = publicador.publicar(new PublicarMensajeRequest(exchange, routingKey,
                    "VALIDACION listener " + UUID.randomUUID()));
        } catch (RuntimeException e) {
            return new Prueba(false, false, false, dlqAntes, dlqAntes, false, "No se pudo publicar: " + e.getMessage());
        }
        if (!pub.confirmado() || !pub.ruteado()) {
            return new Prueba(true, pub.confirmado(), false, dlqAntes, dlqAntes, false, pub.detalle());
        }
        boolean consumida = false;
        long limite = System.currentTimeMillis() + ESPERA_CONSUMO_MS;
        while (System.currentTimeMillis() < limite) {
            pausa(400);
            if (contar(cola) == 0) {
                consumida = true;
                break;
            }
        }
        pausa(1500);
        long dlqDespues = contar(dlq);
        boolean enDlq = dlqDespues > dlqAntes;
        String detalle = !consumida ? "El mensaje sigue en la cola: el listener no lo consumio en " + ESPERA_CONSUMO_MS / 1000 + " s."
                : enDlq ? "El listener lo rechazo y el mensaje paso a la DLQ."
                : "Publicado en " + exchange + " con '" + routingKey + "', confirmado por el broker, consumido por el listener y sin pasar a la DLQ.";
        return new Prueba(true, true, consumida, dlqAntes, dlqDespues, enDlq, detalle);
    }

    /** Mensajes listos en la cola, en tiempo real (consulta AMQP pasiva, sin el retraso de las estadisticas). */
    private long contar(String cola) {
        QueueInformation info = admin.obtenerInfoCola(cola);
        return info.getMessageCount();
    }

    private void pausa(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
