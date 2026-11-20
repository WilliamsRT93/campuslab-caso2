package cl.duoc.campuslab.kafkaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cl.duoc.campuslab.kafkaadmin.config.KafkaAdminProperties;
import cl.duoc.campuslab.kafkaadmin.dto.GenerarCargaRequest;
import cl.duoc.campuslab.kafkaadmin.dto.GenerarCargaResponse;
import cl.duoc.campuslab.kafkaadmin.dto.PublicarPruebaKafkaRequest;
import cl.duoc.campuslab.kafkaadmin.dto.PublicarPruebaKafkaResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/** Verifica que el caso VALIDO publique JSON parseable y el MALFORMADO publique JSON roto a proposito. */
class PublicadorPruebaKafkaServiceTest {

    private static final KafkaAdminProperties PROPS = new KafkaAdminProperties(
            Map.of(
                    "ventas", new KafkaAdminProperties.TopicoConfig("campuslab.ventas", 3, 2, 604800000L, "delete"),
                    "auditoria", new KafkaAdminProperties.TopicoConfig("campuslab.auditoria", 3, 2, 2592000000L, "delete")),
            ".dlt");

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, String> kafkaTemplateQueResponde(String topico) {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        SendResult<String, String> sendResult = new SendResult<>(
                new ProducerRecord<>(topico, "k", "v"),
                new RecordMetadata(new TopicPartition(topico, 0), 0, 0, 0, 0, 0));
        when(kafkaTemplate.send(eq(topico), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        return kafkaTemplate;
    }

    @Test
    void publicaUnaVentaValidaConJsonParseable() {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateQueResponde("campuslab.ventas");
        PublicadorPruebaKafkaService service = new PublicadorPruebaKafkaService(kafkaTemplate, PROPS);

        PublicarPruebaKafkaResponse respuesta = service.publicar(new PublicarPruebaKafkaRequest("ventas", "VALIDO"));

        assertThat(respuesta.topico()).isEqualTo("campuslab.ventas");
        assertThat(respuesta.payloadEnviado()).contains("\"reservaId\"").contains("\"estado\": \"SOLICITADA\"");
    }

    @Test
    void publicaUnaAuditoriaMalformadaQueNoEsJsonValido() {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateQueResponde("campuslab.auditoria");
        PublicadorPruebaKafkaService service = new PublicadorPruebaKafkaService(kafkaTemplate, PROPS);

        PublicarPruebaKafkaResponse respuesta = service.publicar(new PublicarPruebaKafkaRequest("auditoria", "MALFORMADO"));

        assertThat(respuesta.topico()).isEqualTo("campuslab.auditoria");
        // JSON a proposito sin cerrar: new com.fasterxml.jackson.databind.ObjectMapper().readTree(...) debe fallar.
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(respuesta.payloadEnviado()));
        assertThat(respuesta.detalle()).contains(".dlt");
    }

    @Test
    void enviaAlTopicoPublicadoPorLaPropiedadDeConfiguracion() {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateQueResponde("campuslab.ventas");
        PublicadorPruebaKafkaService service = new PublicadorPruebaKafkaService(kafkaTemplate, PROPS);

        service.publicar(new PublicarPruebaKafkaRequest("ventas", "VALIDO"));

        org.mockito.Mockito.verify(kafkaTemplate).send(eq("campuslab.ventas"), any(), argThat((String p) -> p.contains("reservaId")));
    }

    @Test
    void generaCargaPublicandoLaCantidadDeMensajesPedida() {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateQueResponde("campuslab.ventas");
        PublicadorPruebaKafkaService service = new PublicadorPruebaKafkaService(kafkaTemplate, PROPS);

        GenerarCargaResponse respuesta = service.generarCarga(new GenerarCargaRequest("ventas", 10));

        assertThat(respuesta.topico()).isEqualTo("campuslab.ventas");
        assertThat(respuesta.mensajesPublicados()).isEqualTo(10);
        org.mockito.Mockito.verify(kafkaTemplate, org.mockito.Mockito.times(10)).send(eq("campuslab.ventas"), any(), any());
    }

    @Test
    void generarCargaConTopicoLogicoDesconocidoLanzaSolicitudInvalida() {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateQueResponde("campuslab.ventas");
        PublicadorPruebaKafkaService service = new PublicadorPruebaKafkaService(kafkaTemplate, new KafkaAdminProperties(Map.of(), ".dlt"));

        assertThatThrownBy(() -> service.generarCarga(new GenerarCargaRequest("ventas", 10)))
                .isInstanceOf(cl.duoc.campuslab.kafkaadmin.exception.SolicitudInvalidaException.class);
    }
}
