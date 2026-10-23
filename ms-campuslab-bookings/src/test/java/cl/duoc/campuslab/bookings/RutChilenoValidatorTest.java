package cl.duoc.campuslab.bookings;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pruebas del digito verificador chileno (modulo 11) sobre el esquema de la reserva. */
class RutChilenoValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private List<String> erroresDeRut(String rut) {
        ReservaRequest request = new ReservaRequest();
        request.setRut(rut);
        return validator.validateProperty(request, "rut").stream().map(v -> v.getMessage()).toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11111111-1", "12345678-5", "12531909-2", "7654321-6", "20000000-5", "10000013-K", "10000013-k", "10000004-0"})
    void aceptaRutConDigitoVerificadorCorrecto(String rut) {
        assertThat(erroresDeRut(rut)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678-9", "11111111-2", "9876543-2", "10000013-1", "10000004-K"})
    void rechazaDigitoVerificadorIncorrecto(String rut) {
        assertThat(erroresDeRut(rut)).hasSize(1);
        assertThat(erroresDeRut(rut).get(0)).startsWith("El rut no es valido: el digito verificador de");
    }

    @Test
    void informaElDigitoEsperado() {
        assertThat(erroresDeRut("12345678-9"))
                .containsExactly("El rut no es valido: el digito verificador de 12345678 es 5, no 9");
    }

    @Test
    void conFormatoInvalidoSoloReportaElFormato() {
        assertThat(erroresDeRut("111111111")).containsExactly("El rut debe tener formato 12345678-9 (sin puntos)");
    }
}
