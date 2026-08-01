package dev.portfolio.wbmon;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton-контейнер: поднимается один раз на весь прогон и живёт до конца JVM,
 * убирает его Ryuk. Аннотацию @Testcontainers со static-полем в базовом классе
 * использовать нельзя — она останавливает контейнер после КАЖДОГО тест-класса,
 * из-за чего второй класс получает новый порт и ломает кэш Spring-контекстов.
 */
public abstract class AbstractPostgresTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("wbmon.poll.tick-ms", () -> "3600000"); // не поллить во время тестов
    }
}
