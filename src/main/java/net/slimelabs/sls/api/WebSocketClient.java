package net.slimelabs.sls.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neovisionaries.ws.client.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static net.slimelabs.sls.api.HttpClient.CLIENT_API_KEY;

public class WebSocketClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private WebSocketMessageListener listener;  // Message listener to pass messages
    private WebSocket ws;
    private String apiKey = CLIENT_API_KEY;
    private final String serverId;
    private String token;

    public WebSocketClient(String serverId) {
        this.serverId = serverId;
    }

    public void addMessageListener(WebSocketMessageListener listener) {
        this.listener = listener;
    }

    public void connect() throws Exception {
        // Step 1: Retrieve WebSocket credentials
        String endpoint = "http://panel.slimelabs.net/api/client/servers/" + serverId + "/websocket";
        JsonNode response = get(endpoint, CLIENT_API_KEY);
        String websocketUrl;
        if (response != null && response.has("data")) {
            JsonNode data = response.get("data");
            websocketUrl = data.get("socket").asText();
            token = data.get("token").asText();
        } else {
            throw new RuntimeException("Failed to retrieve WebSocket credentials.");
        }

        // Step 2: Establish WebSocket connection
        ws = new WebSocketFactory()
                .createSocket(websocketUrl)
                .addListener(new WebSocketAdapter() {
                    @Override
                    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
                        authenticate();
                    }

                    @Override
                    public void onTextMessage(WebSocket websocket, String message) {
                        if (listener != null) {
                            listener.onMessageReceived(message);  // Pass message to the listener
                        }
                        handleMessage(message);
                    }

                    @Override
                    public void onError(WebSocket websocket, WebSocketException cause) {
                        System.err.println("WebSocket error: " + cause.getMessage());
                    }
                })
                .connect();
    }

    public void closeConnection() {
        listener = null;
        if (ws != null && ws.isOpen()) {
            ws.disconnect();
        }
    }

    private void authenticate() {
        sendMessage("{\"event\":\"auth\",\"args\":[\"" + token + "\"]}");
    }

    private void refreshToken() {
        try {
            // Retrieve new token
            String endpoint = "http://panel.slimelabs.net/api/client/servers/" + serverId + "/websocket";
            JsonNode response = get(endpoint, apiKey);
            if (response != null && response.has("data")) {
                JsonNode data = response.get("data");
                token = data.get("token").asText();
                authenticate();
            } else {
                throw new RuntimeException("Failed to refresh token.");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private void sendMessage(String message) {
        if (ws != null && ws.isOpen()) {
            ws.sendText(message);
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode jsonMessage = objectMapper.readTree(message);
            String event = jsonMessage.get("event").asText();
            switch (event) {
                case "auth success":
                    break;
                case "token expiring", "token expired":
                    refreshToken();
                    break;
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    // Static method to perform a GET request and return a JsonNode
    public static JsonNode get(String endpoint, String apiKey) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(endpoint);
            request.addHeader("Authorization", "Bearer " + apiKey);
            try (CloseableHttpResponse response = client.execute(request)) {
                return objectMapper.readTree(response.getEntity().getContent());
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }
}
