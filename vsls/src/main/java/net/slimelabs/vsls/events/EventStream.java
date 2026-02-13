package net.slimelabs.vsls.events;

import com.protoxon.S4J.client.entities.*;
import net.slimelabs.vsls.log.Log;

import java.util.function.Consumer;

public class EventStream {

    private final WebSocketEventStream stream;

    public EventStream(WebSocketEventStream stream) {
        this.stream = stream;
        // Log stream errors
        stream.onError(this::logError);
    }

    public void onServerEvent(Consumer<ServerEvent> listener) {
        stream.onServerEvent(listener);
    }

    public void onError(Consumer<Throwable> listener) {
        stream.onError(listener);
    }

    public void start() {
        stream.start();
        Log.info("Initializing event stream");
    }

    public void stop() {
        stream.stop();
    }

    public void logError(Throwable error) {
        // EOFException is a normal close
        if (error instanceof java.io.EOFException) {
            Log.info("Event Stream: WebSocket closed (EOF)");
            return;
        }

        String message = error != null ? error.getMessage() : "null";
        if (message == null || message.isEmpty()) {
            message = error.getClass().getSimpleName();
        }

        if (error instanceof java.io.IOException) {
            Log.debug("Event Stream: WebSocket closed: " + message);
        } else {
            Log.error("Event Stream: Websocket error: " + message);
            if (error != null && error.getCause() != null) {
                Log.error("  Cause: " + error.getCause().getMessage());
            }
        }
    }


}
