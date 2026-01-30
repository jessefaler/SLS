package com.protoxon.S4J.client.entities.impl;

import com.protoxon.S4J.client.entities.ServerEvent;
import com.protoxon.S4J.client.entities.ServerEventParser;
import com.protoxon.S4J.client.entities.WebSocketEventStream;
import com.protoxon.S4J.entities.S4J;
import com.protoxon.S4J.requests.Route;
import com.protoxon.S4J.requests.WebSocketEvent;
import com.protoxon.S4J.requests.WSAction;

import java.util.function.Consumer;

public class WebSocketEventStreamImpl implements WebSocketEventStream {

    private final S4J api;
    private WSAction<Void> wsAction;
    private Consumer<WebSocketEvent> rawEventHandler;
    private Consumer<ServerEvent> serverEventHandler;
    private Consumer<Throwable> errorHandler;

    public WebSocketEventStreamImpl(S4J api) {
        this.api = api;
    }

    @Override
    public void start() {
        if (wsAction != null && wsAction.isStreaming()) {
            return;
        }

        wsAction = new WSAction<Void>(api, Route.Events.WEBSOCKET_EVENTS.compile()) {
            @Override
            public void onEvent(WebSocketEvent event) {
                if (rawEventHandler != null) {
                    rawEventHandler.accept(event);
                }
                if (serverEventHandler != null) {
                    ServerEvent parsed = ServerEventParser.parse(event);
                    if (parsed != null) {
                        serverEventHandler.accept(parsed);
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                if (errorHandler != null) {
                    errorHandler.accept(t);
                }
                super.onError(t);
            }
        };

        wsAction.start();
    }

    @Override
    public void stop() {
        if (wsAction != null) {
            wsAction.stop();
            wsAction = null;
        }
    }

    @Override
    public boolean isStreaming() {
        return wsAction != null && wsAction.isStreaming();
    }

    @Override
    public WebSocketEventStream onEvent(Consumer<WebSocketEvent> handler) {
        this.rawEventHandler = handler;
        return this;
    }

    @Override
    public WebSocketEventStream onServerEvent(Consumer<ServerEvent> handler) {
        this.serverEventHandler = handler;
        return this;
    }

    @Override
    public WebSocketEventStream onError(Consumer<Throwable> handler) {
        this.errorHandler = handler;
        return this;
    }
}

