package net.slimelabs.vsls.events;

import com.velocitypowered.api.scheduler.ScheduledTask;
import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class Event<T> {

    private final List<ListenerEntry<T>> listeners = new CopyOnWriteArrayList<>();
    private static final class ListenerEntry<T> {
        private final T listener;
        private Handle handle;

        private ListenerEntry(T listener) {
            this.listener = listener;
        }
    }

    /**
     * Subscribe a listener to this event.
     */
    public Handle subscribe(T listener) {
        ListenerEntry<T> entry = new ListenerEntry<>(listener);
        Handle handle = new Handle(() -> listeners.remove(entry));
        entry.handle = handle;
        listeners.add(entry);
        return handle;
    }

    /**
     * Fire the event
     */
    public void fire(BiConsumer<T, Handle> dispatcher) {
        for (ListenerEntry<T> entry : listeners) {
            try {
                dispatcher.accept(entry.listener, entry.handle);
            } catch (Throwable t) {
                Log.error("Event listener threw exception", t);
            }
        }
    }

    /**
     * Remove all listeners.
     */
    public void clear() {
        listeners.clear();
    }

    // ==============================
    // Handle
    // ==============================

    public static final class Handle implements AutoCloseable {

        private final Runnable remover;
        private volatile boolean removed = false;
        private ScheduledTask timeoutTask;

        private Handle(Runnable remover) {
            this.remover = remover;
        }

        /**
         * Automatically remove this listener after a duration.
         */
        public synchronized Handle timeout(long duration, TimeUnit unit, Runnable onTimeout) {
            if (timeoutTask != null) {
                timeoutTask.cancel();
            }

            timeoutTask = SLS.proxy.getScheduler()
                    .buildTask(SLS.plugin, () -> {
                        try {
                            if (onTimeout != null) {
                                onTimeout.run();
                            }
                        } finally {
                            remove();
                        }
                    })
                    .delay(duration, unit)
                    .schedule();

            return this;
        }

        // Overload for convenience if no callback is needed
        public synchronized Handle timeout(long duration, TimeUnit unit) {
            return timeout(duration, unit, null);
        }

        /**
         * Remove this listener manually.
         */
        public synchronized void remove() {
            if (removed) return;
            removed = true;

            if (timeoutTask != null) {
                timeoutTask.cancel();
                timeoutTask = null;
            }

            remover.run();
        }

        @Override
        public void close() {
            remove();
        }
    }
}