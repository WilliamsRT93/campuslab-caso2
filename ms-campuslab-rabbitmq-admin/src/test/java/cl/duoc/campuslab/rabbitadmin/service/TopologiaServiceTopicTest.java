package cl.duoc.campuslab.rabbitadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Reglas del exchange topic: "*" = una palabra, "#" = cero o mas palabras. */
class TopologiaServiceTopicTest {

    @Test
    void almohadillaCoincideConCeroOMasPalabras() {
        assertThat(TopologiaService.coincideTopic("reserva.#", "reserva.creada")).isTrue();
        assertThat(TopologiaService.coincideTopic("reserva.#", "reserva.creada.hoy")).isTrue();
        assertThat(TopologiaService.coincideTopic("reserva.#", "reserva")).isTrue();
        assertThat(TopologiaService.coincideTopic("catalogo.#", "catalogo.producto.creacion")).isTrue();
        assertThat(TopologiaService.coincideTopic("#", "cualquier.cosa")).isTrue();
    }

    @Test
    void asteriscoCoincideConExactamenteUnaPalabra() {
        assertThat(TopologiaService.coincideTopic("reserva.*", "reserva.creada")).isTrue();
        assertThat(TopologiaService.coincideTopic("reserva.*", "reserva.creada.hoy")).isFalse();
        assertThat(TopologiaService.coincideTopic("reserva.*", "reserva")).isFalse();
        assertThat(TopologiaService.coincideTopic("*.producto.*", "catalogo.producto.creacion")).isTrue();
    }

    @Test
    void unPatronNoCoincideConOtroDominio() {
        assertThat(TopologiaService.coincideTopic("reserva.#", "catalogo.producto.creacion")).isFalse();
        assertThat(TopologiaService.coincideTopic("catalogo.#", "reserva.creada")).isFalse();
        assertThat(TopologiaService.coincideTopic("reserva.creada", "reserva.cancelada")).isFalse();
    }
}
