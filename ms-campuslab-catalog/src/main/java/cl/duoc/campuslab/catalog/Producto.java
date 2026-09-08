package cl.duoc.campuslab.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Recurso reservable del catalogo: un laboratorio, un equipo o un insumo.
 * En la jerga del encargo son los "productos de prueba" que administra el rol Admin.
 */
@Entity
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre visible del recurso (ej: "Laboratorio de Redes 3"). */
    private String nombre;

    /** Tipo de recurso: LABORATORIO, EQUIPO o INSUMO. */
    private String tipo;

    /** Cupo o stock disponible. Disminuye cuando se aprueba una reserva. */
    private Integer stock;

    /** Indica si el recurso puede reservarse. */
    private Boolean disponible;

    public Producto() {
    }

    public Producto(String nombre, String tipo, Integer stock, Boolean disponible) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.stock = stock;
        this.disponible = disponible;
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

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public Boolean getDisponible() {
        return disponible;
    }

    public void setDisponible(Boolean disponible) {
        this.disponible = disponible;
    }
}
