package net.slimelabs.vsls.server;

import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entites.*;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.utils.TimeUtils;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class Events {

    WebSocketEventStream events;
    ServerProvider provider;

    public Events(SLSClient api, ServerProvider provider) {
        this.events = api.getEventStream();
        this.provider = provider;
        initEventStream();
    }

    public static Events init(SLSClient api, ServerProvider provider) {
        return new Events(api, provider);
    }

    public void initEventStream() {
        events.onServerEvent(event -> {
            String id = event.getServerId();
            Server server = provider.getServer(id);

            if (event instanceof StatusUpdateEvent statusEvent) {

                // ================================
                // Status Change Event
                // ================================
                ServerStatus status = statusEvent.getStatus();
                // Log the status change to debug output channels
                logStatusChange(status.getStatus(), id);
                if(server == null) return;
                // Update the servers status
                server.status = status;
                // Call the servers status change handler
                server.handleStatusChange(status);
                // Notify Listeners
                server.fireStatusChange(status);

            } else if (event instanceof ServerCrashEvent crashEvent) {

                // ================================
                // Crash Event
                // ================================
                Log.warn("Server " + id + " crashed:\n" +
                        " - Reason: " + crashEvent.getReason() + "\n" +
                        " - Exit Code: " + crashEvent.getExitCode() + "\n" +
                        " - Timestamp: " + TimeUtils.formatTimestamp(crashEvent.getTimestamp()));
                if(server == null) return;
                // Call the servers crash handler
                server.handleCrash(crashEvent);
                // Notify Listeners
                server.fireCrash(crashEvent);

            } else if (event instanceof ServerDeletedEvent) {

                // ================================
                // Deletion Event
                // ================================
                if(server == null) return;
                // Handle server deletion events
                server.handleDeletion();
                Log.info("Server {} was deleted", id);

            }
        });

        events.onError(error -> {
            // EOFException is a normal close
            if (error instanceof java.io.EOFException) {
                Log.debug("Event Stream: WebSocket closed normally (EOF)");
                return;
            }
            
            String message = error != null ? error.getMessage() : "null";
            if (message == null || message.isEmpty()) {
                message = error != null ? error.getClass().getSimpleName() : "Unknown error";
            }
            
            // IOException is usually a normal close
            if (error instanceof java.io.IOException) {
                Log.debug("Event Stream: WebSocket closed: " + message);
            } else {
                Log.error("Event Stream: Websocket error: " + message);
                if (error != null && error.getCause() != null) {
                    Log.error("  Cause: " + error.getCause().getMessage());
                }
            }
        });

        events.start();
        Log.info("Initialized event listener");
    }

    /**
     * Closes the event stream websocket
     */
    public void stop() {
        events.stop();
    }

    // Logs a status change to the debug log level
    // With nice formatting for debug players
    public void logStatusChange(String status, String id) {
        Log.target(Log.Target.CONSOLE).debug("Server {} changed status to {}", id, status);
        Log.target(Log.Target.PLAYER).sendMessage(
                ProtoMessage.chat()
                        .add(MessagePreset.SLS)
                        .addMiniMessage(String.format(
                                "<hover:show_text:'<dark_purple>%s</dark_purple>'><dark_gray>[</dark_gray><gray>DEBUG</gray><dark_gray>] </dark_gray>" +
                                        "<gray>Server </gray><dark_gray>(</dark_gray><red>%s</red><dark_gray>)</dark_gray>" +
                                        "<gray> changed status to: </gray><red>%s</red></hover>", Log.getTimestamp(), id, status
                        )));
    }

}
