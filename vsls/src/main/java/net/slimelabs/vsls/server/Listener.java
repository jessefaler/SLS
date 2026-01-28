package net.slimelabs.vsls.server;


import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.client.entites.ServerCrashEvent;
import com.protoxon.S4J.client.entites.ServerDeletedEvent;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.slimelabs.vsls.SLS;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class Listener {

    private final List<StatusChangeListener> statusListeners = new CopyOnWriteArrayList<>();
    private final List<ListenerEntry<StatusChangeListenerWithHandle>> statusListenersWithHandle = new CopyOnWriteArrayList<>();
    private final List<CrashListener> crashListeners = new CopyOnWriteArrayList<>();
    private final List<ListenerEntry<CrashListenerWithHandle>> crashListenersWithHandle = new CopyOnWriteArrayList<>();
    private final List<DeletionListener> deletionListeners = new CopyOnWriteArrayList<>();
    private final List<ListenerEntry<DeletionListenerWithHandle>> deletionListenersWithHandle = new CopyOnWriteArrayList<>();

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
    public interface DeletionListener {
        void onDeletion(ServerDeletedEvent deletion);
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
     * Deletion listener that receives its handle,
     * allowing it to unregister itself from within the callback.
     */
    @FunctionalInterface
    public interface DeletionListenerWithHandle {
        void onDeletion(ServerDeletedEvent deletion, Handle handle);
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
     * Registers a listener that is invoked whenever the server is deleted.
     *
     * @param listener the listener to notify on deletion events
     * @return a handle that can be used to unregister the listener.
     *         The handle may be safely ignored if you intend for the listener
     *         to remain for the lifetime of the server, as it will be removed
     *         automatically when the server is unregistered.
     */
    public Handle onDeletion(DeletionListener listener) {
        deletionListeners.add(listener);
        return new Handle(() -> {
            deletionListeners.remove(listener);
        });
    }

    /**
     * Registers a listener that is invoked whenever the server is deleted.
     * The listener receives its handle, allowing it to unregister itself.
     *
     * @param listener the listener to notify on deletion events
     * @return a handle that can be used to unregister the listener.
     *         The handle may be safely ignored if you intend for the listener
     *         to remain for the lifetime of the server, as it will be removed
     *         automatically when the server is unregistered.
     */
    public Handle onDeletion(DeletionListenerWithHandle listener) {
        Handle handle = new Handle(() -> {
            deletionListenersWithHandle.removeIf(entry -> entry.listener == listener);
        });
        deletionListenersWithHandle.add(new ListenerEntry<>(listener, handle));
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

    protected void fireDeletion(ServerDeletedEvent deletion) {
        // Fire regular listeners
        for (DeletionListener l : deletionListeners) {
            l.onDeletion(deletion);
        }
        // Fire self-unregistering listeners with their stored handle
        for (ListenerEntry<DeletionListenerWithHandle> entry : deletionListenersWithHandle) {
            entry.listener.onDeletion(deletion, entry.handle);
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
        deletionListeners.clear();
        deletionListenersWithHandle.clear();
    }

    public static class Handle {

        Runnable remove;
        private ScheduledTask timeoutTask;

        public Handle(Runnable remove) {
            this.remove = remove;
        }

        /**
         * Automatically removes this listener handle after the specified duration.
         * If the listener is manually removed before the timeout, the scheduled task is cancelled.
         *
         * @param duration the duration to wait before automatically removing the listener
         * @param unit the time unit of the duration
         * @return this handle for method chaining
         */
        public Handle timeout(long duration, TimeUnit unit) {
            if (timeoutTask != null) {
                // If a timeout is already set, cancel it first
                timeoutTask.cancel();
            }
            
            timeoutTask = SLS.proxy.getScheduler()
                    .buildTask(SLS.plugin, () -> {
                        remove();
                        timeoutTask = null;
                    })
                    .delay(duration, unit)
                    .schedule();
            
            return this;
        }

        /**
         * Removes this listener handle.
         * If a timeout was set, it will be cancelled.
         */
        public void remove() {
            if (timeoutTask != null) {
                timeoutTask.cancel();
                timeoutTask = null;
            }
            remove.run();
        }
    }

}