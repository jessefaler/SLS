package net.slimelabs.vsls.events;

import net.slimelabs.vsls.log.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventRouter {

    private final EventStream stream;
    private final Map<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();

    public EventRouter(EventStream stream) {
        this.stream = stream;
        subscribe();
    }

    /**
     * Subscribes to events from the event stream
     */
    private void subscribe() {
        stream.onServerEvent(this::dispatch);
    }

    @SuppressWarnings("unchecked")
    private <T> void dispatch(T event) {
        List<Consumer<?>> consumers = handlers.get(event.getClass());
        if (consumers == null) return;

        for (Consumer<?> consumer : consumers) {
            try {
                ((Consumer<T>) consumer).accept(event);
            } catch (Throwable t) {
                Log.error("EventRouter: handler threw exception", t);
            }
        }
    }

    public <T> void on(Class<T> type, Consumer<T> handler) {
        handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
    }

}