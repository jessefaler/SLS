package com.protoxon.S4J.requests;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.entities.S4J;
import com.protoxon.S4J.utils.S4JLogger;
import okhttp3.WebSocket;
import org.slf4j.Logger;

import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class WSAction<T> extends SLSActionImpl<T> {

    public static final Logger LOGGER = S4JLogger.getLogger(SLSAction.class);

    private final AtomicReference<WebSocket> activeWebSocket = new AtomicReference<>();

    // --- Reconnection settings ---
    private final long initialDelayMs = 200;      // 200ms
    private final long maxDelayMs = 10_000;        // 10 seconds
    private long currentDelayMs = initialDelayMs;

    private volatile boolean manualStop = false;
    
    // Track if we've already logged a connection failure to avoid spam
    private final AtomicBoolean hasLoggedFailure = new AtomicBoolean(false);

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
                        // Reset failure log flag when we successfully receive a message
                        hasLoggedFailure.set(false);

                    } catch (Throwable t) {
                        onError(t);
                    }
                },
                error -> {
                    // Check if this is a normal closure that shouldn't be logged as an error
                    boolean isNormalClosure = false;
                    if (error instanceof EOFException) {
                        isNormalClosure = true;
                    } else if (error instanceof IOException && error.getMessage() != null) {
                        String msg = error.getMessage();
                        // Normal closure messages from onClosed handler
                        if (msg.contains("WebSocket closed: 1000") || msg.contains("closed normally")) {
                            isNormalClosure = true;
                        }
                    } else if (error == null) {
                        // Null error might indicate a normal closure
                        isNormalClosure = true;
                    }
                    
                    // Only log actual connection failures, not normal closures
                    // Only log the first failure, suppress subsequent ones until connection is re-established
                    if (!isNormalClosure && hasLoggedFailure.compareAndSet(false, true)) {
                        String errorMessage = (error != null && error.getMessage() != null) 
                            ? error.getMessage() 
                            : (error != null ? error.getClass().getSimpleName() : "Unknown WebSocket error");
                        // Use Requester logger to match original log format
                        org.slf4j.Logger requesterLog = com.protoxon.S4J.utils.S4JLogger.getLogger(com.protoxon.S4J.requests.Requester.class);
                        requesterLog.error("WebSocket failure: {}", errorMessage);
                    }
                    onError(error);
                }
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

