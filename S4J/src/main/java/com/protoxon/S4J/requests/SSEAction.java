package com.protoxon.S4J.requests;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.entites.S4J;
import com.protoxon.S4J.utils.S4JLogger;
import okhttp3.Call;
import org.slf4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public abstract class SSEAction<T> extends SLSActionImpl<T> {

    public static final Logger LOGGER = S4JLogger.getLogger(SLSAction.class);

    private final AtomicReference<Call> activeCall = new AtomicReference<>();

    // --- Reconnection settings ---
    private final long initialDelayMs = 200;      // 1 second
    private final long maxDelayMs = 10_000;        // 10 seconds
    private long currentDelayMs = initialDelayMs;

    private volatile boolean manualStop = false;

    // ScheduledExecutorService to handle reconnections
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public SSEAction(S4J api, Route.CompiledRoute route) {
        super(api, route);
    }

    /** Called for every parsed SSE event */
    public abstract void onEvent(EventStream event);

    /** Called on failure */
    public void onError(Throwable t) {
        // EOFException is normal for SSE streams
        if (t instanceof java.io.EOFException) {
            LOGGER.warn("[S4J] SSE Stream ended (EOF)");
        } else {
            LOGGER.warn("[S4J] SSE Error: {}", t.getMessage());
        }

        if (isRetryableError(t)) {
            scheduleReconnect();
        } else {
            LOGGER.warn("[S4J] SSE Fatal Error: {}", t.getMessage());
            stop(); // no more retries
        }
    }

    /**
     * Starts the SSE stream
     */
    public void start() {
        manualStop = false;
        Route.CompiledRoute route = finalizeRoute();

        // this is important:
        Call call = getS4J().getRequester().stream(
                route,
                raw -> {
                    try {
                        if (raw == null || raw.isBlank()) return;
                        EventStream ev = EventStream.parse(raw);
                        onEvent(ev);
                        currentDelayMs = initialDelayMs; // reset backoff on success

                    } catch (Throwable t) {
                        onError(t);
                    }
                },
                this::onError
        );

        activeCall.set(call);
    }

    /** Cancels the stream and prevents further reconnects */
    public void stop() {
        manualStop = true;
        Call call = activeCall.getAndSet(null);
        if (call != null && !call.isCanceled()) {
            call.cancel();
        }

        // Stop any pending reconnections
        scheduler.shutdownNow();
    }

    public boolean isStreaming() {
        Call call = activeCall.get();
        return call != null && !call.isCanceled();
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
        // Normal SSE disconnects
        if (e instanceof java.io.EOFException) return true;
        if (e instanceof java.net.SocketTimeoutException) return true;
        if (e instanceof java.net.SocketException) return true;

        // SSL errors: only retry handshake failures
        if (e instanceof javax.net.ssl.SSLHandshakeException) return false;
        if (e instanceof javax.net.ssl.SSLPeerUnverifiedException) return false;

        // Default: retry
        return true;
    }
}