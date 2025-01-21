import net.slimelabs.sls.api.WebSocketClient;

public class WebSocketClientTest {

    public static void main(String[] args) {
        String serverId = "60dc7ada";
        WebSocketClient client = new WebSocketClient(serverId);
        try {
            client.connect();
            // Keep the main thread alive to maintain the connection
            Thread.currentThread().join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
