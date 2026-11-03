package com.shop.backend.einvoice.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderException;
import com.shop.backend.einvoice.provider.exception.InvoiceProviderTimeoutException;
import com.shop.backend.einvoice.provider.misa.BoundedDeadlineClientHttpRequestFactory;
import com.shop.backend.einvoice.provider.misa.MisaApiClient;
import com.shop.backend.einvoice.provider.misa.MisaProperties;
import com.shop.backend.einvoice.provider.misa.dto.MisaAuthRequest;
import com.shop.backend.einvoice.provider.misa.dto.MisaAuthResponse;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpBodyDeadlineIntegrationTest {

    private static HttpServer server;
    private static int port;
    private static String baseUrl;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static volatile HttpHandler currentHandler;

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/api/integration/auth/token", exchange -> {
            HttpHandler handler = currentHandler;
            if (handler != null) {
                handler.handle(exchange);
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            }
        });
        server.start();
        port = server.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private MisaApiClient createClient(Duration deadline, int maxBodyBytes) {
        MisaProperties props = new MisaProperties();
        props.setBaseUrl(baseUrl);
        props.setConnectTimeoutSeconds(2);
        props.setReadTimeoutSeconds((int) Math.max(1, deadline.toSeconds()));

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        BoundedDeadlineClientHttpRequestFactory factory = new BoundedDeadlineClientHttpRequestFactory(
                httpClient,
                deadline,
                maxBodyBytes
        );

        RestClient restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();

        return new MisaApiClient(props, objectMapper, restClient);
    }

    @Test
    void test1_headersReturnedImmediately_bodyNeverFinishes_failsWithinTotalDeadline() {
        currentHandler = exchange -> {
            try (InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 1000); // promises 1000 bytes
            OutputStream os = exchange.getResponseBody();
            os.flush();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {
            }
        };

        MisaApiClient client = createClient(Duration.ofMillis(1000), 1024 * 1024);
        MisaAuthRequest request = new MisaAuthRequest("appid", "tax", "user", "pass");

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> client.authenticate(request))
                .isInstanceOf(InvoiceProviderTimeoutException.class)
                .satisfies(ex -> {
                    InvoiceProviderException ipe = (InvoiceProviderException) ex;
                    assertThat(ipe.isRetryable()).isTrue();
                });
        long elapsed = System.currentTimeMillis() - start;

        // Bounded within deadline + margin (1000ms deadline, fails well before server's 10s sleep)
        assertThat(elapsed).isGreaterThanOrEqualTo(900);
        assertThat(elapsed).isLessThan(2500);
    }

    @Test
    void test2_slowTrickleBody_chunksSentBeforeIdleTimeout_failsWithinTotalDeadline() {
        // Chunks arrive every 300ms, total 1500ms. Socket idle timeout would not trigger,
        // but total request deadline of 1000ms MUST abort the execution.
        currentHandler = exchange -> {
            try (InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0); // chunked
            OutputStream os = exchange.getResponseBody();
            try {
                for (int i = 0; i < 5; i++) {
                    os.write("{\"chunk\":1}".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Thread.sleep(300);
                }
                os.close();
            } catch (Exception ignored) {
            }
        };

        MisaApiClient client = createClient(Duration.ofMillis(1000), 1024 * 1024);
        MisaAuthRequest request = new MisaAuthRequest("appid", "tax", "user", "pass");

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> client.authenticate(request))
                .isInstanceOf(InvoiceProviderTimeoutException.class)
                .satisfies(ex -> {
                    InvoiceProviderException ipe = (InvoiceProviderException) ex;
                    assertThat(ipe.isRetryable()).isTrue();
                });
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThanOrEqualTo(900);
        assertThat(elapsed).isLessThan(2500);
    }

    @Test
    void test3_bodyStopsMidway_failsWithinTotalDeadline() {
        currentHandler = exchange -> {
            try (InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 500); // promises 500 bytes
            OutputStream os = exchange.getResponseBody();
            os.write("{\"code\":\"200\",\"data\":".getBytes(StandardCharsets.UTF_8));
            os.flush();
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {
            }
        };

        MisaApiClient client = createClient(Duration.ofMillis(1000), 1024 * 1024);
        MisaAuthRequest request = new MisaAuthRequest("appid", "tax", "user", "pass");

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> client.authenticate(request))
                .isInstanceOf(InvoiceProviderTimeoutException.class)
                .satisfies(ex -> {
                    InvoiceProviderException ipe = (InvoiceProviderException) ex;
                    assertThat(ipe.isRetryable()).isTrue();
                    assertThat(ipe.getAttemptOutcome()).isEqualTo(com.shop.backend.einvoice.model.AttemptOutcome.TIMEOUT_UNKNOWN);
                });
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThanOrEqualTo(900);
        assertThat(elapsed).isLessThan(2500);
    }

    @Test
    void test4_bodyCompletesWithinDeadline_succeeds() {
        currentHandler = exchange -> {
            try (InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
            }
            String json = "{\"Success\":true,\"Data\":\"jwt-test-token-abc\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        };

        MisaApiClient client = createClient(Duration.ofMillis(2000), 1024 * 1024);
        MisaAuthRequest request = new MisaAuthRequest("appid", "tax", "user", "pass");

        MisaAuthResponse response = client.authenticate(request);
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("jwt-test-token-abc");
    }

    @Test
    void test5_responseExceedsMaxBodySize_abortsPromptlyWithoutMemoryExhaustion() {
        currentHandler = exchange -> {
            try (InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            byte[] chunk = new byte[8192];
            try {
                for (int i = 0; i < 30; i++) { // ~240KB > 50KB limit
                    os.write(chunk);
                    os.flush();
                }
                os.close();
            } catch (Exception ignored) {
            }
        };

        // Max body size 50KB, server sends ~240KB
        MisaApiClient client = createClient(Duration.ofMillis(3000), 50 * 1024);
        MisaAuthRequest request = new MisaAuthRequest("appid", "tax", "user", "pass");

        assertThatThrownBy(() -> client.authenticate(request))
                .isInstanceOf(InvoiceProviderTimeoutException.class)
                .satisfies(ex -> {
                    InvoiceProviderException ipe = (InvoiceProviderException) ex;
                    assertThat(ipe.isRetryable()).isTrue();
                });
    }

    @Test
    void test6_afterTimeout_serverObservesClientDisconnectOrExchangeTermination() throws Exception {
        AtomicBoolean clientDisconnected = new AtomicBoolean(false);
        CountDownLatch serverExitedLatch = new CountDownLatch(1);

        currentHandler = exchange -> {
            try {
                try (InputStream is = exchange.getRequestBody()) {
                    is.readAllBytes();
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 0); // chunked
                OutputStream os = exchange.getResponseBody();
                for (int i = 0; i < 20; i++) {
                    os.write("{\"data\":\"chunk\"}".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    Thread.sleep(150);
                }
                os.close();
            } catch (IOException e) {
                // Client disconnect/cancellation observed by server
                clientDisconnected.set(true);
            } catch (Exception ignored) {
            } finally {
                serverExitedLatch.countDown();
            }
        };

        MisaApiClient client = createClient(Duration.ofMillis(500), 1024 * 1024);
        MisaAuthRequest request = new MisaAuthRequest("appid", "tax", "user", "pass");

        assertThatThrownBy(() -> client.authenticate(request))
                .isInstanceOf(InvoiceProviderTimeoutException.class);

        // Server terminates exchange upon detecting client disconnect (well within 3s instead of running 20*150=3000ms)
        boolean finished = serverExitedLatch.await(3, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        assertThat(clientDisconnected.get()).isTrue();
    }

    @Test
    void test7_multipleConsecutiveTimeouts_doNotLeakResources_andSubsequentRequestSucceeds() {
        // 5 consecutive timeouts with hung body
        currentHandler = exchange -> {
            try (InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 1000);
            OutputStream os = exchange.getResponseBody();
            os.flush();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ignored) {
            }
        };

        MisaApiClient client = createClient(Duration.ofMillis(300), 1024 * 1024);
        MisaAuthRequest request = new MisaAuthRequest("appid", "tax", "user", "pass");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> client.authenticate(request))
                    .isInstanceOf(InvoiceProviderTimeoutException.class)
                    .satisfies(ex -> assertThat(((InvoiceProviderException) ex).isRetryable()).isTrue());
        }

        // After 5 timeouts, server sends normal valid response
        currentHandler = exchange -> {
            try (InputStream is = exchange.getRequestBody()) {
                is.readAllBytes();
            }
            String json = "{\"Success\":true,\"Data\":\"recovered-token-after-timeouts\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        };

        MisaAuthResponse response = client.authenticate(request);
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("recovered-token-after-timeouts");
    }
}
