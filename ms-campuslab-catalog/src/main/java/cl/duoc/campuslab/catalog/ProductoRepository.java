package cl.duoc.campuslab.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio JPA del catalogo de productos. */
public interface ProductoRepository extends JpaRepository<Producto, Long> {
}
