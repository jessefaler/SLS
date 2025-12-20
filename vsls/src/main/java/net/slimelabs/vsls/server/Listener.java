package net.slimelabs.vsls.server;


import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entites.ServerCrashEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Listener {

    private final List<StatusChangeListener> statusListeners = new CopyOnWriteArrayList<>();
    private final List<ListenerEntry<StatusChangeListenerWithHandle>> statusListenersWithHandle = new CopyOnWriteArrayList<>();
    private final List<CrashListener> crashListeners = new CopyOnWriteArrayList<>();
    private final List<ListenerEntry<CrashListenerWithHandle>> crashListenersWithHandle = new CopyOnWriteArrayList<>();
    private final List<UnregistrationListener> unregistrationListeners = new CopyOnWriteArrayList<>();
    private final List<ListenerEntry<UnregistrationListenerWithHandle>> unregistrationListenersWithHandle = new CopyOnWriteArrayList<>();

    // Helper record to store listener with its handle
    private record ListenerEntry<T>(T listener, Handle handle) {}

    @FunctionalInterface
    public interface StatusChangeListener {
        void onStatusChange(ServerStatus status);
    }

    @FunctionalInterface
    public interface CrashListener {
        void onCrash(ServerCrashEvent crash);
    }

    @FunctionalInterface
    public interface UnregistrationListener {
        void onUnregistration();
    }

    /**
     * Status change listener that receives its handle,
     * allowing it to unregister itself from within the callback.
     */
    @FunctionalInterface
    public interface StatusChangeListenerWithHandle {
        void onStatusChange(ServerStatus status, Handle handle);
    }

    /**
     * Crash listener that receives its handle,
     * allowing it to unregister itself from within the callback.
     */
    @FunctionalInterface
    public interface CrashListenerWithHandle {
        void onCrash(ServerCrashEvent crash, Handle handle);
    }

    /**
     * Unregistration listener that receives its handle,
     * allowing it to unregister itself from within the callback.
     */
    @FunctionalInterface
    public interface UnregistrationListenerWithHandle {
        void onUnregistration(Handle handle);
    }

    /**
     * Registers a listener that is invoked whenever the server's status changes.
     *
     * @param listener the listener to notify on status updates
     * @return a handle that can be used to unregister the listener.
     *         The handle may be safely ignored if you intend for the listener
     *         to remain for the lifetime of the server, as it will be removed
     *         automatically when the server is unregistered.
     */
    public Handle onStatusChange(StatusChangeListener listener) {
        statusListeners.add(listener);
        return new Handle(() -> {
            statusListeners.remove(listener);
        });
    }

    /**
     * Registers a listener that is invoked whenever the server's status changes.
     * The listener receives its handle, allowing it to unregister itself.
     *
     * @param listener the listener to notify on status updates
     * @return a handle that can be used to unregister the listener.
     *         The handle may be safely ignored if you intend for the listener
     *         to remain for the lifetime of the server, as it will be removed
     *         automatically when the server is unregistered.
     */
    public Handle onStatusChange(StatusChangeListenerWithHandle listener) {
        Handle handle = new Handle(() -> {
            statusListenersWithHandle.removeIf(entry -> entry.listener == listener);
        });
        statusListenersWithHandle.add(new ListenerEntry<>(listener, handle));
        return handle;
    }

    /**
     * Registers a listener that is invoked whenever the server crashes.
     *
     * @param listener the listener to notify on crash events
     * @return a handle that can be used to unregister the listener.
     *         The handle may be safely ignored if you intend for the listener
     *         to remain for the lifetime of the server, as it will be removed
     *         automatically when the server is unregistered.
     */
    public Handle onCrash(CrashListener listener) {
        crashListeners.add(listener);
        return new Handle(() -> {
            crashListeners.remove(listener);
        });
    }

    /**
     * Registers a listener that is invoked whenever the server crashes.
     * The listener receives its handle, allowing it to unregister itself.
     *
     * @param listener the listener to notify on crash events
     * @return a handle that can be used to unregister the listener.
     *         The handle may be safely ignored if you intend for the listener
     *         to remain for the lifetime of the server, as it will be removed
     *         automatically when the server is unregistered.
     */
    public Handle onCrash(CrashListenerWithHandle listener) {
        Handle handle = new Handle(() -> {
            crashListenersWithHandle.removeIf(entry -> entry.listener == listener);
        });
        crashListenersWithHandle.add(new ListenerEntry<>(listener, handle));
        return handle;
    }

    /**
     * Registers a listener that is invoked whenever the server is unregistered.
     *
     * @param listener the listener to notify on unregistration
     * @return a handle that can be used to unregister the listener.
     *         The handle may be safely ignored if you intend for the listener
     *         to remain for the lifetime of the server, as it will be removed
     *         automatically when the server is unregistered.
     */
    public Handle onUnregistration(UnregistrationListener listener) {
        unregistrationListeners.add(listener);
        return new Handle(() -> {
            unregistrationListeners.remove(listener);
        });
    }

    /**
     * Registers a listener that is invoked whenever the server is unregistered.
     * The listener receives its handle, allowing it to unregister itself.
     *
     * @param listener the listener to notify on unregistration
     * @return a handle that can be used to unregister the listener.
     *         The handle may be safely ignored if you intend for the listener
     *         to remain for the lifetime of the server, as it will be removed
     *         automatically when the server is unregistered.
     */
    public Handle onUnregistration(UnregistrationListenerWithHandle listener) {
        Handle handle = new Handle(() -> {
            unregistrationListenersWithHandle.removeIf(entry -> entry.listener == listener);
        });
        unregistrationListenersWithHandle.add(new ListenerEntry<>(listener, handle));
        return handle;
    }

    protected void fireStatusChange(ServerStatus status) {
        // Fire regular listeners
        for (StatusChangeListener l : statusListeners) {
            l.onStatusChange(status);
        }
        // Fire self-unregistering listeners with their stored handle
        for (ListenerEntry<StatusChangeListenerWithHandle> entry : statusListenersWithHandle) {
            entry.listener.onStatusChange(status, entry.handle);
        }
    }

    protected void fireCrash(ServerCrashEvent crash) {
        // Fire regular listeners
        for (CrashListener l : crashListeners) {
            l.onCrash(crash);
        }
        // Fire self-unregistering listeners with their stored handle
        for (ListenerEntry<CrashListenerWithHandle> entry : crashListenersWithHandle) {
            entry.listener.onCrash(crash, entry.handle);
        }
    }

    protected void fireUnregistration() {
        // Fire regular listeners
        for (UnregistrationListener l : unregistrationListeners) {
            l.onUnregistration();
        }
        // Fire self-unregistering listeners with their stored handle
        for (ListenerEntry<UnregistrationListenerWithHandle> entry : unregistrationListenersWithHandle) {
            entry.listener.onUnregistration(entry.handle);
        }
    }

    /**
     * Clears all listeners for the server
     * This is called when a server is unregistered to
     * prevent memory leaks
     */
    public void clearListeners() {
        statusListeners.clear();
        statusListenersWithHandle.clear();
        crashListeners.clear();
        crashListenersWithHandle.clear();
        unregistrationListeners.clear();
        unregistrationListenersWithHandle.clear();
    }

    public class Handle {

        Runnable remove;

        public Handle(Runnable remove) {
            this.remove = remove;
        }

        // Removes this listener handle
        public void remove() {
            remove.run();
        }
    }

}