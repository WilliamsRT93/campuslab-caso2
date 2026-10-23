package cl.duoc.campuslab.bookings.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.QueryTimeoutException;

/** Errores recuperables se reintentan con limite; los no recuperables van a la DLQ. */
class ManejadorErroresListenerTest {

    private RabbitTemplate rabbitTemplate;
    private Channel channel;
    private ManejadorErroresListener manejador;

    @BeforeEach
    void preparar() {
        rabbitTemplate = mock(RabbitTemplate.class);
        channel = mock(Channel.class);
        manejador = new ManejadorErroresListener(rabbitTemplate, 3, 0);
    }

    @Test
    void errorRecuperableSeRepublicaConContadorYSeConfirmaElOriginal() throws Exception {
        manejador.resolver(channel, 7L, "msg", 0, "ex", "rk", "prueba", new ErrorRecuperableException("timeout"));

        ArgumentCaptor<MessagePostProcessor> post = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq("ex"), eq("rk"), eq((Object) "msg"), post.capture());
        Message m = post.getValue().postProcessMessage(new Message("msg".getBytes(), new MessageProperties()));
        assertThat((Object) m.getMessageProperties().getHeader("x-intentos")).isEqualTo(1);
        verify(channel).basicAck(7L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void conReintentosAgotadosVaALaDlq() throws Exception {
        manejador.resolver(channel, 8L, "msg", 3, "ex", "rk", "prueba", new ErrorRecuperableException("timeout"));

        verify(channel).basicNack(8L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    void errorNoRecuperableVaDirectoALaDlqSinReintentar() throws Exception {
        manejador.resolver(channel, 9L, "msg", 0, "ex", "rk", "prueba", new IllegalArgumentException("payload invalido"));

        verify(channel).basicNack(9L, false, false);
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    void siFallaLaRepublicacionElMensajeVaALaDlq() throws Exception {
        doThrow(new AmqpException("broker caido")).when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(Object.class), any(MessagePostProcessor.class));

        manejador.resolver(channel, 10L, "msg", 0, "ex", "rk", "prueba", new ErrorRecuperableException("timeout"));

        verify(channel).basicNack(10L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void clasificaLosErrores() {
        assertThat(manejador.esRecuperable(new ErrorRecuperableException("x"))).isTrue();
        assertThat(manejador.esRecuperable(new QueryTimeoutException("bd lenta"))).isTrue();
        assertThat(manejador.esRecuperable(new java.net.SocketTimeoutException("timeout"))).isTrue();
        assertThat(manejador.esRecuperable(new IllegalArgumentException("invalido"))).isFalse();
        assertThat(manejador.esRecuperable(new NullPointerException())).isFalse();
    }

    @Test
    void falloTransitorioSimuladoFallaSoloEnLosPrimerosIntentos() {
        assertThatThrownBy(() -> manejador.simularFalloTransitorio("hola FALLO_TRANSITORIO", 0))
                .isInstanceOf(ErrorRecuperableException.class);
        assertThatThrownBy(() -> manejador.simularFalloTransitorio("hola FALLO_TRANSITORIO", 1))
                .isInstanceOf(ErrorRecuperableException.class);
        assertThatCode(() -> manejador.simularFalloTransitorio("hola FALLO_TRANSITORIO", 2)).doesNotThrowAnyException();
        assertThatThrownBy(() -> manejador.simularFalloTransitorio("hola FALLO_TRANSITORIO:5", 4))
                .isInstanceOf(ErrorRecuperableException.class);
        assertThatCode(() -> manejador.simularFalloTransitorio("mensaje normal", 0)).doesNotThrowAnyException();
    }
}
