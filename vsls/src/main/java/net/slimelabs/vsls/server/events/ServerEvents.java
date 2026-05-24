package net.slimelabs.vsls.server.events;

import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entities.ServerCrashEvent;
import com.protoxon.S4J.client.entities.ServerDeletedEvent;
import com.protoxon.S4J.client.entities.ServerEvent;
import net.slimelabs.vsls.events.Event;

/**
 * Event Listeners for events emitted by a single server instance
 * <p>
 * Fired by {@link ServerEventRouter} while dispatching the incoming event stream.
 */
public class ServerEvents {

    private final Event<EventListener> serverEvent = new Event<>();
    private final Event<StatusListener> statusEvent = new Event<>();
    private final Event<CrashListener> crashEvent = new Event<>();
    private final Event<DeletionListener> deletionEvent = new Event<>();

    @FunctionalInterface
    public interface StatusListener {
        void onStatus(ServerStatus status, Event.Handle handle);
    }

    @FunctionalInterface
    public interface CrashListener {
        void onCrash(ServerCrashEvent crash, Event.Handle handle);
    }

    @FunctionalInterface
    public interface DeletionListener {
        void onDelete(ServerDeletedEvent deletion, Event.Handle handle);
    }

    @FunctionalInterface
    public interface EventListener {
        void onEvent(ServerEvent event, Event.Handle handle);
    }

    public Event.Handle onServerEvent(EventListener listener) {
        return serverEvent.subscribe(listener);
    }

    public Event.Handle onStatusChange(StatusListener listener) {
        return statusEvent.subscribe(listener);
    }

    public Event.Handle onCrash(CrashListener listener) {
        return crashEvent.subscribe(listener);
    }

    public Event.Handle onDeletion(DeletionListener listener) {
        return deletionEvent.subscribe(listener);
    }

    /* internal firing methods */

    void fireEvent(ServerEvent e) {
        serverEvent.fire((listener, handle) ->
                listener.onEvent(e, handle));
    }

    void fireStatus(ServerStatus status) {
        statusEvent.fire((listener, handle) ->
                listener.onStatus(status, handle));
    }

    void fireCrash(ServerCrashEvent crash) {
        crashEvent.fire((listener, handle) ->
                listener.onCrash(crash, handle));
    }

    void fireDeletion(ServerDeletedEvent deletion) {
        deletionEvent.fire((listener, handle) ->
                listener.onDelete(deletion, handle));
    }

    /**
     * Remove all listeners for all server events.
     */
    public void clearAllListeners() {
        serverEvent.clear();
        statusEvent.clear();
        crashEvent.clear();
        deletionEvent.clear();
    }

}
