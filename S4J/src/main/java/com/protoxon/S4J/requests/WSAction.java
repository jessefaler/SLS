package com.protoxon.S4J.requests;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.entites.S4J;
import com.protoxon.S4J.utils.S4JLogger;
import okhttp3.WebSocket;
import org.slf4j.Logger;

import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public abstract class WSAction<T> extends SLSActionImpl<T> {

    public static final Logger LOGGER = S4JLogger.getLogger(SLSAction.class);

    private final AtomicReference<WebSocket> activeWebSocket = new AtomicReference<>();

    // --- Reconnection settings ---
    private final long initialDelayMs = 200;      // 200ms
    private final long maxDelayMs = 10_000;        // 10 seconds
    private long currentDelayMs = initialDelayMs;

    private volatile boolean manualStop = false;

    // ScheduledExecutorService to handle reconnections
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public WSAction(S4J api, Route.CompiledRoute route) {
        super(api, route);
    }

    /** Called for every parsed WebSocket event */
    public abstract void onEvent(WebSocketEvent event);

    /** Called on failure */
    public void onError(Throwable t) {
        if (t instanceof EOFException) {
            LOGGER.debug("[S4J] WebSocket connection closed normally (EOF)");
            scheduleReconnect();
            return;
        }

        if (t instanceof IOException) {
            String message = t.getMessage();
            if (message == null || message.isEmpty()) {
                message = t.getClass().getSimpleName();
            }
            LOGGER.debug("[S4J] WebSocket connection closed: {}", message);
        } else if (t != null) {
            String message = t.getMessage();
            if (message == null || message.isEmpty()) {
                message = t.getClass().getSimpleName();
            }
            LOGGER.warn("[S4J] WebSocket Error: {}", message);
        } else {
            LOGGER.debug("[S4J] WebSocket connection closed (null error)");
        }

        if (t == null || isRetryableError(t)) {
            scheduleReconnect();
        } else {
            String message = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            LOGGER.warn("[S4J] WebSocket Fatal Error: {}", message);
            stop(); // no more retries
        }
    }

    /**
     * Starts the WebSocket stream
     */
    public void start() {
        manualStop = false;
        Route.CompiledRoute route = finalizeRoute();

        WebSocket webSocket = getS4J().getRequester().websocket(
                route,
                message -> {
                    try {
                        if (message == null || message.isEmpty()) return;
                        WebSocketEvent ev = WebSocketEvent.parse(message);
                        onEvent(ev);
                        currentDelayMs = initialDelayMs; // reset backoff on success

                    } catch (Throwable t) {
                        onError(t);
                    }
                },
                this::onError
        );

        activeWebSocket.set(webSocket);
    }

    /** Cancels the stream and prevents further reconnects */
    public void stop() {
        manualStop = true;
        WebSocket webSocket = activeWebSocket.getAndSet(null);
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
        }

        // Stop any pending reconnections
        scheduler.shutdownNow();
    }

    public boolean isStreaming() {
        WebSocket webSocket = activeWebSocket.get();
        return webSocket != null;
    }

    // Schedules reconnect using exponential backoff
    private void scheduleReconnect() {
        if (manualStop) return;

        // Clamp maximum delay
        long delay = Math.min(currentDelayMs, maxDelayMs);

        // Schedule the reconnection task
        scheduler.schedule(() -> {
            if (!manualStop) {
                start();
            }

            // Exponential increase for next retry
            currentDelayMs = Math.min(currentDelayMs * 2, maxDelayMs);
        }, delay, TimeUnit.MILLISECONDS);
    }

    private boolean isRetryableError(Throwable e) {
        if (e instanceof EOFException) return true;
        // Normal WebSocket disconnects
        if (e instanceof java.io.IOException) return true;
        if (e instanceof java.net.SocketTimeoutException) return true;
        if (e instanceof java.net.SocketException) return true;

        // SSL errors: only retry handshake failures
        if (e instanceof javax.net.ssl.SSLHandshakeException) return false;
        if (e instanceof javax.net.ssl.SSLPeerUnverifiedException) return false;

        // Default: retry
        return true;
    }
}

