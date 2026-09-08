package cl.duoc.campuslab.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Prueba basica: el contexto arranca y los productos de prueba quedan sembrados. */
@SpringBootTest
class CatalogApplicationTests {

    @Autowired
    private ProductoRepository repository;

    @Test
    void contextLoadsAndSeedsProducts() {
        assertThat(repository.count()).isGreaterThan(0);
    }
}
