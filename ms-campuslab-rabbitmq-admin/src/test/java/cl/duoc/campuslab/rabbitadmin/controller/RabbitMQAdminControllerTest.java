package cl.duoc.campuslab.rabbitadmin.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.campuslab.rabbitadmin.exception.RabbitAdminExceptionHandler;
import cl.duoc.campuslab.rabbitadmin.exception.RecursoExistenteException;
import cl.duoc.campuslab.rabbitadmin.exception.RecursoNoEncontradoException;
import cl.duoc.campuslab.rabbitadmin.service.RabbitAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Contrato HTTP del administrador: validacion de entrada y codigos de error con mensajes claros. */
class RabbitMQAdminControllerTest {

    private RabbitAdminService servicio;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        servicio = mock(RabbitAdminService.class);
        mvc = MockMvcBuilders.standaloneSetup(new RabbitMQAdminController(servicio))
                .setControllerAdvice(new RabbitAdminExceptionHandler()).build();
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String ruta, String json) throws Exception {
        return mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void colaConNombreVacioDa400ConElDetalleDelCampo() throws Exception {
        postJson("/api/rabbitmq/queues", "{\"nombre\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La validacion de la solicitud fallo"))
                .andExpect(jsonPath("$.errores.nombre").exists());
        verify(servicio, never()).crearCola(anyString(), anyBoolean());
    }

    @Test
    void colaConNombreCortoOConSimbolosDa400() throws Exception {
        postJson("/api/rabbitmq/queues", "{\"nombre\":\"ab\"}").andExpect(status().isBadRequest());
        postJson("/api/rabbitmq/queues", "{\"nombre\":\"mi cola!\"}").andExpect(status().isBadRequest());
    }

    @Test
    void elPrefijoReservadoAmqSeRechazaConUnMensajeClaro() throws Exception {
        postJson("/api/rabbitmq/queues", "{\"nombre\":\"amq.prueba\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre").value(org.hamcrest.Matchers.containsString("amq.")));
        postJson("/api/rabbitmq/exchanges", "{\"nombre\":\"amq.prueba\"}").andExpect(status().isBadRequest());
    }

    @Test
    void colaValidaDa201YPasaLaOpcionDeColaMuerta() throws Exception {
        postJson("/api/rabbitmq/queues", "{\"nombre\":\"cola-demo\",\"conDeadLetter\":true}")
                .andExpect(status().isCreated());
        verify(servicio).crearCola("cola-demo", true);
    }

    @Test
    void jsonMalFormadoDa400() throws Exception {
        postJson("/api/rabbitmq/queues", "{nombre:").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void bindingSinCamposDa400ConLosTresErrores() throws Exception {
        postJson("/api/rabbitmq/bindings", "{\"cola\":\"\",\"exchange\":\"\",\"routingKey\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cola").exists())
                .andExpect(jsonPath("$.errores.exchange").exists())
                .andExpect(jsonPath("$.errores.routingKey").exists());
    }

    @Test
    void eliminarUnaColaInexistenteDa404ConMensaje() throws Exception {
        doThrow(new RecursoNoEncontradoException("La cola 'x' no existe")).when(servicio).eliminarCola("x");
        mvc.perform(delete("/api/rabbitmq/queues/x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La cola 'x' no existe"));
    }

    @Test
    void crearUnaColaQueYaExisteDa409() throws Exception {
        doThrow(new RecursoExistenteException("La cola 'cola-demo' ya existe")).when(servicio).crearCola("cola-demo", false);
        postJson("/api/rabbitmq/queues", "{\"nombre\":\"cola-demo\"}").andExpect(status().isConflict());
    }

    @Test
    void consultarUnaColaInexistenteDa404() throws Exception {
        when(servicio.obtenerInfoCola("x")).thenThrow(new RecursoNoEncontradoException("La cola 'x' no existe"));
        mvc.perform(get("/api/rabbitmq/queues/x")).andExpect(status().isNotFound());
    }
}
