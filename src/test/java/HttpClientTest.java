import net.slimelabs.sls.api.HttpClient;

public class HttpClientTest {

    public static void main(String[] args) throws Exception {
        //System.out.println(HttpClient.getServerIdByName("test"));
        //HttpClient.startServer("4f2ee1f5");
        String state = HttpClient.getServerState("cfa1e951");
        System.out.println("state: " + state);
    }
}
