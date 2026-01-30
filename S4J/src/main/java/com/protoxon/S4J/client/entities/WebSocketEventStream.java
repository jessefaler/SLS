package com.protoxon.S4J.client.entities;

import com.protoxon.S4J.requests.WebSocketEvent;

import java.util.function.Consumer;

public interface WebSocketEventStream {
    void start();
    void stop();
    boolean isStreaming();

    WebSocketEventStream onEvent(Consumer<WebSocketEvent> handler);
    WebSocketEventStream onServerEvent(Consumer<ServerEvent> handler);
    WebSocketEventStream onError(Consumer<Throwable> handler);
}

