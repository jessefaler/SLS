package net.slimelabs.vsls.server;

import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entities.*;
import com.protoxon.S4J.exceptions.NotFoundException;
import com.protoxon.S4J.exceptions.SLSException;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.utils.TimeUtils;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

public class Events {

    WebSocketEventStream events;
    SLSClient api;
    ServerProvider provider;

    public Events(SLSClient api, ServerProvider provider) {
        this.api = api;
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

            // If the server is null try to fetch the server from the api and register before proceeding
            if(server == null) {
                try {
                    ClientServer clientServer = api.getServer(id).execute();
                    // The server was returned by the api add it to the manager and proceed
                    server = SLS.servers.loadServer(clientServer);
                } catch (NotFoundException e) {
                    // If the server was not found skip this event and log a message
                    Log.warn("Events: unknown server with id " + event.getServerId() + " emitted a " + event.getClass());
                    return;
                } catch (SLSException e) {
                    Log.warn("Events: failed to fetch server " + id + ": " + e.getMessage());
                    return;
                }
            }

            if (event instanceof StatusUpdateEvent statusEvent) {

                // ================================
                // Status Change Event
                // ================================
                ServerStatus status = statusEvent.getStatus();
                // Log the status change to debug output channels
                logStatusChange(status.getStatus(), id);
                // Update the servers status
                server.status = status;
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
                // Notify Listeners
                server.fireCrash(crashEvent);

            } else if (event instanceof ServerDeletedEvent deletionEvent) {

                // ================================
                // Deletion Event
                // ================================
                // Fire an offline status change for the server
                server.fireStatusChange(ServerStatus.OFFLINE);
                // Notify deletion event listeners
                server.fireDeletion(deletionEvent);
                // Handle server deletion events
                server.unregister();
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
        Log.info("Initialized Event Listener");
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
