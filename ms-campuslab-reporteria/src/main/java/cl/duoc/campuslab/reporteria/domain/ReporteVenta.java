package cl.duoc.campuslab.reporteria.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** Fila del panel de ventas en tiempo real: una por cada evento de reserva/venta consumido. */
@Entity
public class ReporteVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reservaId;
    private String nombre;
    private String rut;
    private String fecha;
    private Long productoId;
    private String estado;
    private Instant recibidoEn;

    protected ReporteVenta() {
    }

    public ReporteVenta(Long reservaId, String nombre, String rut, String fecha, Long productoId, String estado) {
        this.reservaId = reservaId;
        this.nombre = nombre;
        this.rut = rut;
        this.fecha = fecha;
        this.productoId = productoId;
        this.estado = estado;
        this.recibidoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getReservaId() {
        return reservaId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRut() {
        return rut;
    }

    public String getFecha() {
        return fecha;
    }

    public Long getProductoId() {
        return productoId;
    }

    public String getEstado() {
        return estado;
    }

    public Instant getRecibidoEn() {
        return recibidoEn;
    }
}
