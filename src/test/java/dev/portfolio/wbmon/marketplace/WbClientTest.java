package dev.portfolio.wbmon.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import dev.portfolio.wbmon.domain.Marketplace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WbClientTest {

    private static final String PATH = "/cards/v4/detail";

    // Форма ответа снята с живого card.wb.ru 2026-08-01: products[] лежит в КОРНЕ (обёртки "data" нет),
    // price.product — в копейках. Числа здесь синтетические, структура — реальная.
    private static final String VALID_BODY = """
            {"products":[{"id":12345,"name":"Тестовый товар","totalQuantity":7,
              "sizes":[{"price":{"basic":249900,"product":198500,"logistics":0,"return":0}}]}]}
            """;

    private WireMockServer wireMock;
    private WbClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        // backoff 1 мс с множителем 1 — тест не должен спать 13 секунд
        client = new WbClient("http://localhost:" + wireMock.port(),
                500, 500, 3, 1L, 1L, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void reportsItsMarketplace() {
        assertThat(client.marketplace()).isEqualTo(Marketplace.WB);
    }

    @Test
    void parsesPriceInRublesAndQuantity() {
        wireMock.stubFor(get(urlPathEqualTo(PATH))
                .withQueryParam("nm", equalTo("12345"))
                .willReturn(okJson(VALID_BODY)));

        ProductSnapshot snapshot = client.fetch("12345");

        assertThat(snapshot.skuCode()).isEqualTo("12345");
        assertThat(snapshot.price()).isEqualByComparingTo("1985.00");
        assertThat(snapshot.quantity()).isEqualTo(7);
    }

    @Test
    void retriesTransientFailuresAndSucceedsOnThirdAttempt() {
        wireMock.stubFor(get(urlPathEqualTo(PATH)).inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(serverError())
                .willSetStateTo("second"));
        wireMock.stubFor(get(urlPathEqualTo(PATH)).inScenario("retry")
                .whenScenarioStateIs("second")
                .willReturn(serverError())
                .willSetStateTo("third"));
        wireMock.stubFor(get(urlPathEqualTo(PATH)).inScenario("retry")
                .whenScenarioStateIs("third")
                .willReturn(okJson(VALID_BODY)));

        ProductSnapshot snapshot = client.fetch("12345");

        assertThat(snapshot.quantity()).isEqualTo(7);
        wireMock.verify(3, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void throwsAfterFourAttempts() {
        wireMock.stubFor(get(urlPathEqualTo(PATH)).willReturn(serverError()));

        assertThatThrownBy(() -> client.fetch("12345"))
                .isInstanceOf(MarketplaceFetchException.class)
                .hasMessageContaining("after 4 attempts");

        wireMock.verify(4, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void failsFastWhenPayloadShapeIsUnexpected() {
        // Именно так WB отвечает на несуществующий/недоступный артикул: 200 + пустой products[]
        wireMock.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(okJson("{\"products\":[]}")));

        assertThatThrownBy(() -> client.fetch("12345"))
                .isInstanceOf(UnexpectedResponseException.class)
                .hasMessageContaining("12345");

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void failsFastWhenBodyIsNotJson() {
        wireMock.stubFor(get(urlPathEqualTo(PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Access denied</body></html>")));

        assertThatThrownBy(() -> client.fetch("12345"))
                .isInstanceOf(UnexpectedResponseException.class);

        wireMock.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
    }
}
