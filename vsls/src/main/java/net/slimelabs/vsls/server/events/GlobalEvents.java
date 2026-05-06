package net.slimelabs.vsls.server.events;

import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entities.ServerCrashEvent;
import com.protoxon.S4J.client.entities.ServerDeletedEvent;
import com.protoxon.S4J.client.entities.ServerEvent;
import net.slimelabs.vsls.events.Event;
import net.slimelabs.vsls.server.Server;

/**
 * Aggregate event hub for all servers.
 * <p>
 * Fired by {@link ServerEventRouter} while dispatching the incoming event stream.
 */
public class GlobalEvents {

    private final Event<EventListener> serverEvent = new Event<>();
    private final Event<StatusListener> statusEvent = new Event<>();
    private final Event<CrashListener> crashEvent = new Event<>();
    private final Event<DeletionListener> deletionEvent = new Event<>();

    @FunctionalInterface
    public interface StatusListener {
        void onStatus(Server server, ServerStatus status, Event.Handle handle);
    }

    @FunctionalInterface
    public interface CrashListener {
        void onCrash(Server server, ServerCrashEvent crash, Event.Handle handle);
    }

    @FunctionalInterface
    public interface DeletionListener {
        void onDelete(Server server, ServerDeletedEvent deletion, Event.Handle handle);
    }

    @FunctionalInterface
    public interface EventListener {
        void onEvent(Server server, ServerEvent event, Event.Handle handle);
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

    // Fired by ServerEventRouter
    public void fireEvent(Server server, ServerEvent e) {
        serverEvent.fire((listener, handle) -> listener.onEvent(server, e, handle));
    }

    // Fired by ServerEventRouter
    public void fireStatus(Server server, ServerStatus status) {
        statusEvent.fire((listener, handle) -> listener.onStatus(server, status, handle));
    }

    // Fired by ServerEventRouter
    public void fireCrash(Server server, ServerCrashEvent crash) {
        crashEvent.fire((listener, handle) -> listener.onCrash(server, crash, handle));
    }

    // Fired by ServerEventRouter
    public void fireDeletion(Server server, ServerDeletedEvent deletion) {
        deletionEvent.fire((listener, handle) -> listener.onDelete(server, deletion, handle));
    }

    public void clearAllListeners() {
        serverEvent.clear();
        statusEvent.clear();
        crashEvent.clear();
        deletionEvent.clear();
    }
}

