package net.slimelabs.vsls.server;

import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entites.*;
import net.slimelabs.vsls.log.Log;

public class Events {

    WebSocketEventStream eventStream;
    ServerProvider provider;

    public Events(SLSClient api, ServerProvider provider) {
        this.eventStream = api.getEventStream();
        this.provider = provider;
        initEventStream();
    }

    public static Events init(SLSClient api, ServerProvider provider) {
        return new Events(api, provider);
    }

    public void initEventStream() {
        eventStream.onServerEvent(event -> {
            String id = event.getServerId();
            Server server = provider.getServer(id);

            if (event instanceof StatusUpdateEvent statusEvent) {
                Log.debug("Server {} changed status to {}", statusEvent.getServerId(), statusEvent.getStatus());
                if(server == null) return;
                // Handle server status updates
                ServerStatus status = statusEvent.getStatus();
                server.status = status;
                server.onStatusChange(status);
            } else if (event instanceof ServerCrashEvent crashEvent) {
                if(server == null) return;
                // Handle server crashes
                server.onCrash(crashEvent);
                Log.info("Server " + id + " crashed:");
                Log.info("  Reason: " + crashEvent.getReason());
                Log.info("  Exit Code: " + crashEvent.getExitCode());
                Log.info("  Timestamp: " + crashEvent.getTimestamp());
            } else if (event instanceof ServerDeletedEvent) {
                if(server == null) return;
                // Handle server deletion events
                server.onDeletion();
                Log.info("Server {} was deleted", id);
            }
        });

        eventStream.onError(error -> {
            // EOFException is a normal close - don't log as error
            if (error instanceof java.io.EOFException) {
                Log.debug("Event Stream: WebSocket closed normally (EOF)");
                return;
            }
            
            String message = error != null ? error.getMessage() : "null";
            if (message == null || message.isEmpty()) {
                message = error != null ? error.getClass().getSimpleName() : "Unknown error";
            }
            
            // IOException is usually a normal close - log at debug level
            if (error instanceof java.io.IOException) {
                Log.debug("Event Stream: WebSocket closed: " + message);
            } else {
                Log.error("Event Stream: Websocket error: " + message);
                if (error != null && error.getCause() != null) {
                    Log.error("  Cause: " + error.getCause().getMessage());
                }
            }
        });

        eventStream.start();
        Log.info("Initialized event listener");
    }

}
