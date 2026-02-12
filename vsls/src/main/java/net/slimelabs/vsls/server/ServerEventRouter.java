package net.slimelabs.vsls.server;

import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entities.*;
import net.slimelabs.vsls.events.EventRouter;
import net.slimelabs.vsls.log.Log;
import net.slimelabs.vsls.utils.TimeUtils;
import net.slimelabs.vsls.utils.message.MessagePreset;
import net.slimelabs.vsls.utils.message.ProtoMessage;

/**
 * Routes incoming server events to their appropriate handlers.
 * <p>
 * The ServerEventRouter listens for events of type {@link StatusUpdateEvent},
 * {@link ServerCrashEvent}, and {@link ServerDeletedEvent}. For each event, it:
 * <ul>
 *     <li>Delegates the event to the specific handler for that type.</li>
 *     <li>Triggers any registered listeners for the event.</li>
 * </ul>
 * <p>
 */
public class ServerEventRouter {

    private final ServerProvider provider;

    public ServerEventRouter(EventRouter router, ServerProvider provider) {
        this.provider = provider;

        router.on(StatusUpdateEvent.class, this::handleServerEvent);
        router.on(ServerCrashEvent.class, this::handleServerEvent);
        router.on(ServerDeletedEvent.class, this::handleServerEvent);
    }

    private void dispatchEvent(Server server, ServerEvent event) {
        switch (event) {
            case StatusUpdateEvent  statusEvent  -> handleStatusUpdate(server, statusEvent);
            case ServerCrashEvent   crashEvent   -> handleCrash(server, crashEvent);
            case ServerDeletedEvent deletedEvent -> handleDeletion(server, deletedEvent);
        }
        server.getEvents().fireEvent(event);
    }

    public void handleServerEvent(ServerEvent event) {
        provider.getOrFetch(event.getServerId())
                .ifPresentOrElse(server -> dispatchEvent(server, event),
                        () -> Log.warn("Events: unknown server with id {} emitted a {}",
                                event.getServerId(), event.getClass()));
    }

    private void handleStatusUpdate(Server server, StatusUpdateEvent event) {
        ServerStatus status = event.getStatus();
        server.setStatus(status);
        server.getEvents().fireStatus(status);
        logStatusChange(status, server.getShortId());
    }

    private void handleCrash(Server server, ServerCrashEvent event) {
        server.getEvents().fireCrash(event);
        Log.warn("Server {} crashed: Reason={}, ExitCode={}, Timestamp={}",
                server.getShortId(), event.getReason(), event.getExitCode(),
                TimeUtils.formatTimestamp(event.getTimestamp()));
    }

    private void handleDeletion(Server server, ServerDeletedEvent event) {
        server.getEvents().fireStatus(ServerStatus.OFFLINE);
        server.getEvents().fireDeletion(event);
        server.unregister();
        Log.info("Server {} was deleted", server.getShortId());
    }

    // Logs a status change to the debug log level
    // With nice formatting for debug players
    public void logStatusChange(ServerStatus status, String id) {
        Log.target(Log.Target.CONSOLE).debug("Server {} changed status to {}", id, status.getStatus());
        Log.target(Log.Target.PLAYER).sendMessage(
                ProtoMessage.chat()
                        .add(MessagePreset.SLS)
                        .addMiniMessage(String.format(
                                "<hover:show_text:'<dark_purple>%s</dark_purple>'><dark_gray>[</dark_gray><gray>DEBUG</gray><dark_gray>] </dark_gray>" +
                                        "<gray>Server </gray><dark_gray>(</dark_gray><red>%s</red><dark_gray>)</dark_gray>" +
                                        "<gray> changed status to: </gray><red>%s</red></hover>", Log.getTimestamp(), id, status.getStatus()
                        )));
    }
}
