package cl.duoc.campuslab.rabbitadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cl.duoc.campuslab.rabbitadmin.config.RabbitMQProperties;
import cl.duoc.campuslab.rabbitadmin.config.ValidacionProperties;
import cl.duoc.campuslab.rabbitadmin.config.ValidacionProperties.RutaControl;
import cl.duoc.campuslab.rabbitadmin.config.ValidacionProperties.RutaEsperada;
import cl.duoc.campuslab.rabbitadmin.dto.PublicarMensajeRequest;
import cl.duoc.campuslab.rabbitadmin.dto.PublicarMensajeResponse;
import cl.duoc.campuslab.rabbitadmin.dto.TopologiaResponse;
import cl.duoc.campuslab.rabbitadmin.dto.TopologiaResponse.BindingInfo;
import cl.duoc.campuslab.rabbitadmin.dto.TopologiaResponse.ColaInfo;
import cl.duoc.campuslab.rabbitadmin.dto.ValidacionColasResponse;
import cl.duoc.campuslab.rabbitadmin.dto.ValidacionRutasResponse;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueInformation;

/** Validadores de rutas (contra un gateway simulado) y de colas con listener (con el broker simulado). */
class ValidacionServiceTest {

    private HttpServer gateway;
    private final RabbitMQProperties props = new RabbitMQProperties("campuslab.exchange", "campuslab.dlx",
            Map.of("a", "cola-a"), "-dlq", Map.of("a", "reserva.a"), 60000L,
            new RabbitMQProperties.Topic("campuslab.eventos", Map.of("r", "cola-ev"), Map.of("r", "reserva.#")));

    @AfterEach
    void cerrar() {
        if (gateway != null) {
            gateway.stop(0);
        }
    }

    /** Gateway simulado: solo existen las rutas "declaradas"; si proxy=true responde 200 a cualquier otra ruta. */
    private String levantarGateway(Set<String> declaradas, boolean proxy) throws Exception {
        gateway = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        gateway.createContext("/", ex -> {
            String clave = ex.getRequestMethod() + " " + ex.getRequestURI().getPath();
            boolean declarada = declaradas.contains(clave);
            boolean conToken = ex.getRequestHeaders().getFirst("Authorization") != null;
            int estado = declarada ? (conToken ? (clave.startsWith("POST") ? 400 : 200) : 401) : (proxy ? 200 : 404);
            ex.sendResponseHeaders(estado, -1);
            ex.close();
        });
        gateway.start();
        return "http://127.0.0.1:" + gateway.getAddress().getPort();
    }

    private ValidacionService servicioDeRutas(String url) {
        ValidacionProperties v = new ValidacionProperties(url, 5,
                List.of(new RutaEsperada("GET", "/api/me", null, 200), new RutaEsperada("POST", "/api/bookings", "{}", 400)),
                List.of(new RutaControl("GET", "/api/no-existe"), new RutaControl("DELETE", "/api/bookings")));
        return new ValidacionService(mock(TopologiaService.class), mock(PublicadorPruebaService.class),
                mock(RabbitAdminService.class), props, v);
    }

    @Test
    void rutasProtegidasYSinProxyDanOk() throws Exception {
        String url = levantarGateway(Set.of("GET /api/me", "POST /api/bookings"), false);
        ValidacionRutasResponse r = servicioDeRutas(url).validarRutas("Bearer x");
        assertThat(r.ok()).isTrue();
        assertThat(r.rutasCorrectas()).isEqualTo(2);
        assertThat(r.todasProtegidasConJwt()).isTrue();
        assertThat(r.sinProxy()).isTrue();
        assertThat(r.conclusion()).startsWith("OK");
    }

    @Test
    void siElGatewayTieneProxyLoDetecta() throws Exception {
        String url = levantarGateway(Set.of("GET /api/me", "POST /api/bookings"), true);
        ValidacionRutasResponse r = servicioDeRutas(url).validarRutas("Bearer x");
        assertThat(r.sinProxy()).isFalse();
        assertThat(r.ok()).isFalse();
        assertThat(r.conclusion()).contains("posible proxy");
    }

    @Test
    void siFaltaUnaRutaDeclaradaFalla() throws Exception {
        String url = levantarGateway(Set.of("GET /api/me"), false);
        ValidacionRutasResponse r = servicioDeRutas(url).validarRutas("Bearer x");
        assertThat(r.ok()).isFalse();
        assertThat(r.rutasCorrectas()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ colas

    private TopologiaResponse topologia(long consumidores) {
        return new TopologiaResponse(List.of(),
                List.of(new ColaInfo("cola-a", "negocio", 0, consumidores, 0, 0, 60000L, "campuslab.dlx", "n1"),
                        new ColaInfo("cola-a-dlq", "dlq", 0, 0, 0, 0, null, null, "n1"),
                        new ColaInfo("cola-ev", "negocio", 0, 1, 0, 0, 60000L, "campuslab.dlx", "n1"),
                        new ColaInfo("cola-ev-dlq", "dlq", 0, 0, 0, 0, null, null, "n1")),
                List.of(new BindingInfo("campuslab.exchange", "reserva.a", "cola-a"),
                        new BindingInfo("campuslab.dlx", "reserva.a", "cola-a-dlq"),
                        new BindingInfo("campuslab.eventos", "reserva.#", "cola-ev"),
                        new BindingInfo("campuslab.dlx", "cola-ev", "cola-ev-dlq")),
                "ahora");
    }

    private ValidacionService servicioDeColas(long consumidores, long dlqAntes, long dlqDespues) {
        TopologiaService topo = mock(TopologiaService.class);
        when(topo.obtener()).thenReturn(topologia(consumidores));
        PublicadorPruebaService pub = mock(PublicadorPruebaService.class);
        when(pub.publicar(any(PublicarMensajeRequest.class))).thenReturn(
                new PublicarMensajeResponse("campuslab.exchange", "reserva.a", true, true, List.of("cola-a"), "ok"));
        RabbitAdminService admin = mock(RabbitAdminService.class);
        when(admin.obtenerInfoCola(eq("cola-a"))).thenReturn(new QueueInformation("cola-a", 0, (int) consumidores));
        when(admin.obtenerInfoCola(eq("cola-ev"))).thenReturn(new QueueInformation("cola-ev", 0, 1));
        when(admin.obtenerInfoCola(eq("cola-ev-dlq"))).thenReturn(new QueueInformation("cola-ev-dlq", 0, 0));
        when(admin.obtenerInfoCola(eq("cola-a-dlq"))).thenReturn(
                new QueueInformation("cola-a-dlq", (int) dlqAntes, 0), new QueueInformation("cola-a-dlq", (int) dlqDespues, 0));
        ValidacionProperties v = new ValidacionProperties("http://x", 5, List.of(), List.of());
        return new ValidacionService(topo, pub, admin, props, v);
    }

    @Test
    void colaConListenerBindingDlxTtlYDlqEsValida() {
        ValidacionColasResponse r = servicioDeColas(1, 0, 0).validarColas(false);
        assertThat(r.ok()).isTrue();
        var c = r.colas().get(0);
        assertThat(c.listenerActivo()).isTrue();
        assertThat(c.bindingPrincipal()).isTrue();
        assertThat(c.tieneDlx()).isTrue();
        assertThat(c.tieneTtl()).isTrue();
        assertThat(c.colaMuertaConBinding()).isTrue();
        var topic = r.colas().get(1);
        assertThat(topic.cola()).isEqualTo("cola-ev");
        assertThat(topic.exchange()).isEqualTo("campuslab.eventos");
        assertThat(topic.routingKey()).isEqualTo("reserva.#");
        assertThat(topic.ok()).isTrue();
    }

    @Test
    void colaSinConsumidorNoTieneListenerActivo() {
        ValidacionColasResponse r = servicioDeColas(0, 0, 0).validarColas(false);
        assertThat(r.ok()).isFalse();
        assertThat(r.colas().get(0).listenerActivo()).isFalse();
    }

    @Test
    void laPruebaExtremoAExtremoConfirmaQueElListenerConsume() {
        ValidacionColasResponse r = servicioDeColas(1, 3, 3).validarColas(true);
        assertThat(r.ok()).isTrue();
        var p = r.colas().get(0).prueba();
        assertThat(p.confirmado()).isTrue();
        assertThat(p.consumida()).isTrue();
        assertThat(p.enviadaADlq()).isFalse();
    }

    @Test
    void siElMensajeCaeALaDlqLaPruebaFalla() {
        ValidacionColasResponse r = servicioDeColas(1, 3, 4).validarColas(true);
        assertThat(r.ok()).isFalse();
        assertThat(r.colas().get(0).prueba().enviadaADlq()).isTrue();
    }
}
