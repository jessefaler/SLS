package net.slimelabs.vsls.utils;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.entities.S4J;
import com.protoxon.S4J.requests.SLSActionImpl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Simple HTTP GET requester with retries and exponential backoff.
 * Use for external APIs (e.g. Paper, Minecraft, Spigot). Returns an executable SLSAction
 * so the request runs on S4J's supplier pool when executed async.
 */
public class SimpleRequester {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(10);

    private final RetryConfig retryConfig;
    private final Duration readTimeout;
    private final HttpClient client;

    public SimpleRequester() {
        this(RetryConfig.DEFAULT);
    }

    public SimpleRequester(RetryConfig retryConfig) {
        this(retryConfig, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    public SimpleRequester(RetryConfig retryConfig, Duration connectTimeout, Duration readTimeout) {
        this.retryConfig = retryConfig;
        this.readTimeout = readTimeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    /**
     * Performs a GET with retries and exponential backoff. Blocks until done or all retries exhausted.
     * Only retries on retriable errors: IO/network failures, HTTP 429, and 5xx.
     *
     * @param url the URL to GET
     * @return response body as string, or null on failure or non-200
     */
    public String getBlocking(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(readTimeout)
                .GET()
                .build();
        for (int attempt = 1; attempt <= retryConfig.maxRetries(); attempt++) {
            if (attempt > 1) {
                long delayMs = retryConfig.delayMillisBeforeAttempt(attempt);
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    return response.body();
                }
                if (!isRetriableStatus(status)) {
                    return null;
                }
            } catch (IOException e) {
                if (!isRetriable(e)) {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Returns an SLSAction that performs the GET when executed (on S4J's supplier pool).
     * Use executeAsync(success, failure) to run without blocking.
     *
     * @param api S4J instance (for executor)
     * @param url the URL to GET
     * @return SLSAction yielding the response body string, or null on failure
     */
    public SLSAction<String> get(S4J api, String url) {
        return SLSActionImpl.onExecute(api, () -> getBlocking(url));
    }

    private static boolean isRetriableStatus(int status) {
        return status == 429 || (status >= 500 && status < 600);
    }

    private static boolean isRetriable(Throwable t) {
        if (t instanceof IOException) {
            return true;
        }
        Throwable cause = t.getCause();
        return cause != null && isRetriable(cause);
    }
}
