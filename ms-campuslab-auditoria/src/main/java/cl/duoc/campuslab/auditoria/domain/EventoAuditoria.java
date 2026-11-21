package cl.duoc.campuslab.auditoria.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** Fila del registro de auditoria: quien creo/actualizo/elimino un producto del catalogo, y cuando. */
@Entity
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String accion;
    private Long productoId;
    private String nombre;
    private String tipo;
    private Integer stock;
    private Boolean disponible;
    private Instant recibidoEn;

    protected EventoAuditoria() {
    }

    public EventoAuditoria(String accion, Long productoId, String nombre, String tipo, Integer stock, Boolean disponible) {
        this.accion = accion;
        this.productoId = productoId;
        this.nombre = nombre;
        this.tipo = tipo;
        this.stock = stock;
        this.disponible = disponible;
        this.recibidoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getAccion() {
        return accion;
    }

    public Long getProductoId() {
        return productoId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public Integer getStock() {
        return stock;
    }

    public Boolean getDisponible() {
        return disponible;
    }

    public Instant getRecibidoEn() {
        return recibidoEn;
    }
}
