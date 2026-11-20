package cl.duoc.campuslab.kafkaadmin.service;

import cl.duoc.campuslab.kafkaadmin.config.KafkaAdminProperties;
import cl.duoc.campuslab.kafkaadmin.dto.GenerarCargaRequest;
import cl.duoc.campuslab.kafkaadmin.dto.GenerarCargaResponse;
import cl.duoc.campuslab.kafkaadmin.dto.PublicarPruebaKafkaRequest;
import cl.duoc.campuslab.kafkaadmin.dto.PublicarPruebaKafkaResponse;
import cl.duoc.campuslab.kafkaadmin.exception.SolicitudInvalidaException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * Casos de prueba prefabricados (Swagger "Try it out"): publica un evento VALIDO (debe verse en
 * el panel de reporteria en segundos) o MALFORMADO (debe terminar en el topico .dlt) en
 * campuslab.ventas/campuslab.auditoria. Pensado para demostrar el flujo completo de punta a punta
 * sin tener que crear una reserva o un producto real desde el frontend.
 */
@Service
public class PublicadorPruebaKafkaService {

    private static final long ESPERA_SEGUNDOS = 10;
    private static final long ESPERA_CARGA_SEGUNDOS = 60;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaAdminProperties props;

    public PublicadorPruebaKafkaService(KafkaTemplate<String, String> kafkaTemplate, KafkaAdminProperties props) {
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
    }

    public PublicarPruebaKafkaResponse publicar(PublicarPruebaKafkaRequest request) {
        KafkaAdminProperties.TopicoConfig config = props.configPara(request.topicoLogico());
        if (config == null) {
            throw new SolicitudInvalidaException("No hay un topico configurado para '" + request.topicoLogico() + "'");
        }
        boolean malformado = "MALFORMADO".equals(request.tipoCaso());
        String payload = malformado ? construirPayloadMalformado(request.topicoLogico())
                : construirPayloadValido(request.topicoLogico());
        String clave = UUID.randomUUID().toString();

        try {
            SendResult<String, String> resultado = kafkaTemplate.send(config.nombre(), clave, payload)
                    .get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
            String detalle = malformado
                    ? "Payload malformado publicado a proposito. El consumidor (ms-campuslab-reporteria) lo "
                      + "rechazara como error NO recuperable y terminara en '" + config.nombre() + ".dlt' -- "
                      + "revise ese topico o el panel de reporteria (no debe aparecer una fila nueva valida)."
                    : "Payload valido publicado. Deberia aparecer una fila nueva en GET /api/reporteria/"
                      + request.topicoLogico() + " en los proximos segundos.";
            return new PublicarPruebaKafkaResponse(config.nombre(), payload,
                    resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset(), detalle);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SolicitudInvalidaException("Publicacion interrumpida");
        } catch (ExecutionException | TimeoutException e) {
            throw new SolicitudInvalidaException("Kafka no confirmo la publicacion de prueba: " + e.getMessage());
        }
    }

    /**
     * Prueba de carga configurable (EP5, boton "Generar carga" del frontend): publica
     * "cantidadMensajes" eventos validos EN BURST (sin esperar la confirmacion de cada uno antes
     * de enviar el siguiente) para que el consumer lag suba de forma visible en
     * GET /api/kafka/consumer-groups mientras ms-campuslab-reporteria los procesa, y vuelva a 0
     * segundos despues.
     */
    public GenerarCargaResponse generarCarga(GenerarCargaRequest request) {
        KafkaAdminProperties.TopicoConfig config = props.configPara(request.topicoLogico());
        if (config == null) {
            throw new SolicitudInvalidaException("No hay un topico configurado para '" + request.topicoLogico() + "'");
        }
        int cantidad = request.cantidadMensajes();
        long inicio = System.currentTimeMillis();

        List<CompletableFuture<SendResult<String, String>>> envios = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            String payload = construirPayloadValido(request.topicoLogico());
            envios.add(kafkaTemplate.send(config.nombre(), UUID.randomUUID().toString(), payload));
        }

        try {
            CompletableFuture.allOf(envios.toArray(CompletableFuture[]::new))
                    .get(ESPERA_CARGA_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SolicitudInvalidaException("Generacion de carga interrumpida");
        } catch (ExecutionException | TimeoutException e) {
            throw new SolicitudInvalidaException("Kafka no confirmo toda la carga de prueba: " + e.getMessage());
        }

        long duracionMs = System.currentTimeMillis() - inicio;
        String detalle = "Se publicaron " + cantidad + " mensajes validos en '" + config.nombre() + "' en "
                + duracionMs + " ms. Revise el consumer lag en el panel de topologia: debe subir y luego "
                + "volver a 0 a medida que ms-campuslab-reporteria los procesa.";
        return new GenerarCargaResponse(config.nombre(), cantidad, duracionMs, detalle);
    }

    private String construirPayloadValido(String topicoLogico) {
        Instant ahora = Instant.now();
        if ("ventas".equals(topicoLogico)) {
            return """
                    {"reservaId": %d, "nombre": "Prueba Swagger", "rut": "11111111-1", \
                    "fecha": "%s", "productoId": 1, "estado": "SOLICITADA"}"""
                    .formatted(System.currentTimeMillis() % 100000, ahora);
        }
        return """
                {"accion": "CREADO", "productoId": %d, "nombre": "Producto Prueba Swagger", \
                "tipo": "EQUIPO", "stock": 1, "disponible": true}"""
                .formatted(System.currentTimeMillis() % 100000);
    }

    /** JSON deliberadamente invalido (falta cerrar llave): el parseo del consumidor debe fallar. */
    private String construirPayloadMalformado(String topicoLogico) {
        return "{\"malformado\": true, \"origen\": \"swagger-" + topicoLogico + "\", \"sinCerrar\": true";
    }
}
