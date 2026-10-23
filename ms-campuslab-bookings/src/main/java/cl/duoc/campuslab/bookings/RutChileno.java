package cl.duoc.campuslab.bookings;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * El digito verificador del RUT debe corresponder segun la formula chilena (modulo 11).
 * Solo revisa el digito: el formato (12345678-9) lo valida @Pattern y la obligatoriedad @NotBlank.
 */
@Documented
@Constraint(validatedBy = RutChilenoValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface RutChileno {

    String message() default "El rut no es valido: el digito verificador no corresponde";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
