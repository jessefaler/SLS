package net.slimelabs.sls.api;

// Deprecated use ServerWebSocket located in the server package instead
@Deprecated
public interface WebSocketMessageListener {
    void onMessageReceived(String message);
}