package net.slimelabs.sls.api;

public interface WebSocketMessageListener {
    void onMessageReceived(String message);
}