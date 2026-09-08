package cl.duoc.campuslab.bookings;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

/**
 * Reserva de un recurso academico.
 * Contiene los tres campos de esquema pedidos: nombre (string), rut (string) y fecha (datetime).
 */
@Entity
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre del solicitante. */
    private String nombre;

    /** RUT del solicitante. */
    private String rut;

    /** Fecha y hora de la reserva (datetime). */
    private LocalDateTime fecha;

    /** Id del recurso/producto reservado (referencia al catalogo). */
    private Long productoId;

    @Enumerated(EnumType.STRING)
    private EstadoReserva estado;

    public Reserva() {
    }

    public Reserva(String nombre, String rut, LocalDateTime fecha, Long productoId, EstadoReserva estado) {
        this.nombre = nombre;
        this.rut = rut;
        this.fecha = fecha;
        this.productoId = productoId;
        this.estado = estado;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getRut() {
        return rut;
    }

    public void setRut(String rut) {
        this.rut = rut;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public Long getProductoId() {
        return productoId;
    }

    public void setProductoId(Long productoId) {
        this.productoId = productoId;
    }

    public EstadoReserva getEstado() {
        return estado;
    }

    public void setEstado(EstadoReserva estado) {
        this.estado = estado;
    }
}
