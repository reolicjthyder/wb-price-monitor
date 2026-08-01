package dev.portfolio.wbmon;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WbPriceMonitorApplicationTest extends AbstractPostgresTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsAndSchemaValidates() {
        assertThat(context.getBean(WbPriceMonitorApplication.class)).isNotNull();
    }
}
