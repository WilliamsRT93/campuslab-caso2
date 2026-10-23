package cl.duoc.campuslab.rabbitadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.campuslab.rabbitadmin.config.RabbitMQProperties;
import cl.duoc.campuslab.rabbitadmin.exception.RecursoExistenteException;
import cl.duoc.campuslab.rabbitadmin.exception.RecursoNoEncontradoException;
import cl.duoc.campuslab.rabbitadmin.exception.SolicitudInvalidaException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** El servicio verifica contra el broker el resultado de cada operacion (RabbitAdmin simulado). */
class RabbitAdminServiceTest {

    private RabbitAdmin admin;
    private RabbitTemplate template;
    private RabbitAdminService servicio;

    @BeforeEach
    void preparar() {
        admin = mock(RabbitAdmin.class);
        template = mock(RabbitTemplate.class);
        when(admin.getRabbitTemplate()).thenReturn(template);
        RabbitMQProperties props = new RabbitMQProperties("campuslab.exchange", "campuslab.dlx",
                Map.of("a", "cola-a"), "-dlq", Map.of("a", "reserva.a"), 60000L);
        servicio = new RabbitAdminService(admin, props);
    }

    private void exchangeExiste(boolean existe) {
        when(template.execute(ArgumentMatchers.<ChannelCallback<Boolean>>any())).thenReturn(existe);
    }

    @Test
    void creaUnaColaNuevaYConfirmaQueExiste() {
        when(admin.getQueueInfo("cola-demo")).thenReturn(null, new QueueInformation("cola-demo", 0, 0));
        servicio.crearCola("cola-demo", false);
        verify(admin).declareQueue(any(Queue.class));
    }

    @Test
    void noCreaUnaColaQueYaExiste() {
        when(admin.getQueueInfo("cola-demo")).thenReturn(new QueueInformation("cola-demo", 0, 0));
        assertThatThrownBy(() -> servicio.crearCola("cola-demo", false)).isInstanceOf(RecursoExistenteException.class);
        verify(admin, never()).declareQueue(any(Queue.class));
    }

    @Test
    void rechazaElPrefijoReservadoAmq() {
        assertThatThrownBy(() -> servicio.crearCola("amq.prueba", false))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("amq.");
        verify(admin, never()).declareQueue(any(Queue.class));
    }

    @Test
    void siElBrokerNoCreaLaColaNoDiceQueSeCreo() {
        when(admin.getQueueInfo("cola-demo")).thenReturn(null);
        assertThatThrownBy(() -> servicio.crearCola("cola-demo", false))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("no creo la cola");
    }

    @Test
    void conDeadLetterCreaLaColaMuertaYElBindingAlDlx() {
        when(admin.getQueueInfo("cola-demo")).thenReturn(null, new QueueInformation("cola-demo", 0, 0));
        servicio.crearCola("cola-demo", true);

        ArgumentCaptor<Queue> colas = ArgumentCaptor.forClass(Queue.class);
        verify(admin, times(2)).declareQueue(colas.capture());
        List<Queue> declaradas = colas.getAllValues();
        assertThat(declaradas.get(0).getName()).isEqualTo("cola-demo-dlq");
        assertThat(declaradas.get(1).getName()).isEqualTo("cola-demo");
        assertThat(declaradas.get(1).getArguments())
                .containsEntry("x-dead-letter-exchange", "campuslab.dlx")
                .containsEntry("x-dead-letter-routing-key", "cola-demo");
        ArgumentCaptor<Binding> binding = ArgumentCaptor.forClass(Binding.class);
        verify(admin).declareBinding(binding.capture());
        assertThat(binding.getValue().getExchange()).isEqualTo("campuslab.dlx");
        assertThat(binding.getValue().getDestination()).isEqualTo("cola-demo-dlq");
    }

    @Test
    void eliminarUnaColaInexistenteDa404EnVezDeDecirQueSeElimino() {
        when(admin.getQueueInfo("no-existe")).thenReturn(null);
        assertThatThrownBy(() -> servicio.eliminarCola("no-existe")).isInstanceOf(RecursoNoEncontradoException.class);
        verify(admin, never()).deleteQueue("no-existe");
    }

    @Test
    void eliminaUnaColaExistente() {
        when(admin.getQueueInfo("cola-demo")).thenReturn(new QueueInformation("cola-demo", 0, 0));
        servicio.eliminarCola("cola-demo");
        verify(admin).deleteQueue("cola-demo");
    }

    @Test
    void purgarYConsultarUnaColaInexistenteDan404() {
        when(admin.getQueueInfo("no-existe")).thenReturn(null);
        assertThatThrownBy(() -> servicio.purgarCola("no-existe")).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.obtenerInfoCola("no-existe")).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void rechazaNombresInvalidosEnLaRuta() {
        assertThatThrownBy(() -> servicio.eliminarCola("mi cola!")).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.eliminarExchange("amq.direct")).isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void elBindingExigeQueExistanLaColaYElExchange() {
        when(admin.getQueueInfo("no-existe")).thenReturn(null);
        assertThatThrownBy(() -> servicio.crearBinding("no-existe", "campuslab.exchange", "rk"))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("cola");

        when(admin.getQueueInfo("cola-a")).thenReturn(new QueueInformation("cola-a", 0, 0));
        exchangeExiste(false);
        assertThatThrownBy(() -> servicio.crearBinding("cola-a", "no.existe", "rk"))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("exchange");
        verify(admin, never()).declareBinding(any(Binding.class));
    }

    @Test
    void creaElBindingCuandoTodoExiste() {
        when(admin.getQueueInfo("cola-a")).thenReturn(new QueueInformation("cola-a", 0, 0));
        exchangeExiste(true);
        servicio.crearBinding("cola-a", "campuslab.exchange", "reserva.a");
        verify(admin).declareBinding(any(Binding.class));
    }
}
