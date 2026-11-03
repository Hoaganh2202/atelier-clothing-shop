package com.shop.backend.einvoice.provider.misa;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.AbstractClientHttpRequest;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

/**
 * ClientHttpRequestFactory enforcing a bounded response body size and a strict full-body deadline.
 * Unlike standard JdkClientHttpRequestFactory which returns as soon as HTTP response headers
 * are received, this factory awaits complete reception of the response body within the deadline.
 * It provides explicit Flow.Subscription cancellation and buffer cleanup on timeout, error, or completion.
 */
public class BoundedDeadlineClientHttpRequestFactory implements ClientHttpRequestFactory {

    public static final int DEFAULT_MAX_BODY_SIZE_BYTES = 10 * 1024 * 1024; // 10MB
    private static final Set<String> DISALLOWED_HEADERS = createDisallowedHeaders();

    private final HttpClient httpClient;
    private Duration readTimeout;
    private final int maxBodySizeBytes;

    public BoundedDeadlineClientHttpRequestFactory(HttpClient httpClient, Duration readTimeout) {
        this(httpClient, readTimeout, DEFAULT_MAX_BODY_SIZE_BYTES);
    }

    public BoundedDeadlineClientHttpRequestFactory(HttpClient httpClient, Duration readTimeout, int maxBodySizeBytes) {
        this.httpClient = httpClient;
        this.readTimeout = readTimeout;
        this.maxBodySizeBytes = maxBodySizeBytes;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public int getMaxBodySizeBytes() {
        return maxBodySizeBytes;
    }

    @Override
    public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod) {
        return new BoundedDeadlineClientHttpRequest(httpClient, uri, httpMethod, readTimeout, maxBodySizeBytes);
    }

    private static Set<String> createDisallowedHeaders() {
        Set<String> headers = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        headers.addAll(Set.of("connection", "content-length", "expect", "host", "upgrade"));
        return Collections.unmodifiableSet(headers);
    }

    private static class BoundedDeadlineClientHttpRequest extends AbstractClientHttpRequest {

        private final HttpClient httpClient;
        private final URI uri;
        private final HttpMethod httpMethod;
        private final Duration readTimeout;
        private final int maxBodySizeBytes;
        private final ByteArrayOutputStream requestBodyStream = new ByteArrayOutputStream();

        BoundedDeadlineClientHttpRequest(
                HttpClient httpClient,
                URI uri,
                HttpMethod httpMethod,
                Duration readTimeout,
                int maxBodySizeBytes
        ) {
            this.httpClient = httpClient;
            this.uri = uri;
            this.httpMethod = httpMethod;
            this.readTimeout = readTimeout;
            this.maxBodySizeBytes = maxBodySizeBytes;
        }

        @Override
        public HttpMethod getMethod() {
            return httpMethod;
        }

        @Override
        public URI getURI() {
            return uri;
        }

        @Override
        protected OutputStream getBodyInternal(HttpHeaders headers) {
            return requestBodyStream;
        }

        @Override
        protected ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException {
            byte[] requestBytes = requestBodyStream.toByteArray();
            HttpRequest.BodyPublisher bodyPublisher = requestBytes.length > 0
                    ? HttpRequest.BodyPublishers.ofByteArray(requestBytes)
                    : HttpRequest.BodyPublishers.noBody();

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .method(httpMethod.name(), bodyPublisher);

            if (readTimeout != null && !readTimeout.isZero() && !readTimeout.isNegative()) {
                builder.timeout(readTimeout);
            }

            headers.forEach((name, values) -> {
                if (!DISALLOWED_HEADERS.contains(name)) {
                    for (String value : values) {
                        if (value != null) {
                            builder.header(name, value);
                        }
                    }
                }
            });

            HttpRequest request = builder.build();
            BoundedByteArrayBodySubscriber subscriber = new BoundedByteArrayBodySubscriber(maxBodySizeBytes);
            CompletableFuture<HttpResponse<byte[]>> future = httpClient.sendAsync(
                    request,
                    responseInfo -> subscriber
            );

            HttpResponse<byte[]> response;
            try {
                if (readTimeout != null && !readTimeout.isZero() && !readTimeout.isNegative()) {
                    response = future.get(readTimeout.toMillis(), TimeUnit.MILLISECONDS);
                } else {
                    response = future.get();
                }
            } catch (TimeoutException e) {
                subscriber.cancel();
                future.cancel(true);
                throw new SocketTimeoutException("HTTP request execution exceeded full-body deadline of "
                        + (readTimeout != null ? readTimeout.toMillis() : 0) + " ms");
            } catch (ExecutionException e) {
                subscriber.cancel();
                Throwable cause = e.getCause();
                if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                    throw new SocketTimeoutException("HTTP request execution timed out: " + cause.getMessage());
                }
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                throw new IOException("HTTP request execution failed: " + cause.getMessage(), cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                subscriber.cancel();
                future.cancel(true);
                throw new IOException("HTTP request interrupted", e);
            } finally {
                requestBodyStream.reset();
            }

            return new BoundedDeadlineClientHttpResponse(response);
        }
    }

    private static class BoundedByteArrayBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

        private final int maxBytes;
        private final CompletableFuture<byte[]> resultFuture = new CompletableFuture<>();
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        private int receivedBytes = 0;

        BoundedByteArrayBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletableFuture<byte[]> getBody() {
            return resultFuture;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            synchronized (this) {
                this.subscription = subscription;
                if (resultFuture.isDone()) {
                    try {
                        subscription.cancel();
                    } catch (Throwable ignored) {
                    }
                    return;
                }
                subscription.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            synchronized (this) {
                if (resultFuture.isDone()) {
                    return;
                }
                for (ByteBuffer bb : item) {
                    int remaining = bb.remaining();
                    if (receivedBytes + remaining > maxBytes) {
                        if (subscription != null) {
                            try {
                                subscription.cancel();
                            } catch (Throwable ignored) {
                            }
                        }
                        buffer.reset();
                        resultFuture.completeExceptionally(
                                new IOException("HTTP response body size exceeded maximum limit of " + maxBytes + " bytes")
                        );
                        return;
                    }
                    receivedBytes += remaining;
                    byte[] chunk = new byte[remaining];
                    bb.get(chunk);
                    buffer.write(chunk, 0, chunk.length);
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            synchronized (this) {
                buffer.reset();
                resultFuture.completeExceptionally(throwable);
            }
        }

        @Override
        public void onComplete() {
            synchronized (this) {
                if (resultFuture.isDone()) {
                    buffer.reset();
                    return;
                }
                byte[] bytes = buffer.toByteArray();
                buffer.reset();
                resultFuture.complete(bytes);
            }
        }

        public void cancel() {
            synchronized (this) {
                if (subscription != null) {
                    try {
                        subscription.cancel();
                    } catch (Throwable ignored) {
                    }
                }
                buffer.reset();
                resultFuture.completeExceptionally(
                        new SocketTimeoutException("HTTP request execution cancelled or timed out")
                );
            }
        }
    }

    private static class BoundedDeadlineClientHttpResponse implements ClientHttpResponse {

        private final HttpResponse<byte[]> response;
        private byte[] body;
        private HttpHeaders headers;

        BoundedDeadlineClientHttpResponse(HttpResponse<byte[]> response) {
            this.response = response;
            this.body = response.body() != null ? response.body() : new byte[0];
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return HttpStatusCode.valueOf(response.statusCode());
        }

        @Override
        public String getStatusText() {
            return "";
        }

        @Override
        public HttpHeaders getHeaders() {
            if (this.headers == null) {
                HttpHeaders h = new HttpHeaders();
                response.headers().map().forEach(h::addAll);
                this.headers = HttpHeaders.readOnlyHttpHeaders(h);
            }
            return this.headers;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            this.body = new byte[0];
        }
    }
}
