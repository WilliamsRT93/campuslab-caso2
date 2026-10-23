package cl.duoc.campuslab.bookings;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/** Valida el digito verificador del RUT con el algoritmo modulo 11 (multiplicadores 2..7 de derecha a izquierda). */
public class RutChilenoValidator implements ConstraintValidator<RutChileno, String> {

    private static final Pattern FORMATO = Pattern.compile("^([0-9]{7,8})-([0-9kK])$");

    @Override
    public boolean isValid(String rut, ConstraintValidatorContext context) {
        if (rut == null || rut.isBlank()) {
            return true;
        }
        var m = FORMATO.matcher(rut);
        if (!m.matches()) {
            return true;
        }
        String cuerpo = m.group(1);
        String dv = m.group(2).toUpperCase();
        String esperado = digitoVerificador(cuerpo);
        if (dv.equals(esperado)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                "El rut no es valido: el digito verificador de " + cuerpo + " es " + esperado + ", no " + dv)
                .addConstraintViolation();
        return false;
    }

    static String digitoVerificador(String cuerpo) {
        int suma = 0;
        int mult = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += (cuerpo.charAt(i) - '0') * mult;
            mult = mult == 7 ? 2 : mult + 1;
        }
        int resto = 11 - (suma % 11);
        return resto == 11 ? "0" : resto == 10 ? "K" : String.valueOf(resto);
    }
}
