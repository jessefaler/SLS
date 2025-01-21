package net.slimelabs.sls.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides methods for interaction with the pterodactyl api
 */
// Deprecated the pterodactyl4j library should be used instead via the API class
@Deprecated
public class HttpClient {

    public static final String APPLICATION_API_KEY = Endpoint.APPLICATION_API_KEY.getValue(); // Replace with your API key
    public static final String CLIENT_API_KEY = Endpoint.CLIENT_API_KEY.getValue();
    private static final ObjectMapper objectMapper = new ObjectMapper();  // Jackson's ObjectMapper to parse JSON

    /**
     * Gets all available allocations
     * @return a JsonNode containing the allocations.
     */
    public static JsonNode getAllocations() {
        return get("http://panel.slimelabs.net/api/application/nodes/1/allocations", APPLICATION_API_KEY);
    }

    /**
     * Retrieves all nests
     * @return a JsonNode containing the allocations.
     */
    public static JsonNode getNests() {
        return get("http://panel.slimelabs.net/api/application/nests", APPLICATION_API_KEY);
    }

    /**
     * Gets all available eggs in a nest
     * @return a JsonNode containing the allocations.
     */
    public static JsonNode getEggs(int nest) {
        return get("http://panel.slimelabs.net/api/application/nests/" + nest + "/eggs", APPLICATION_API_KEY);
    }

    /**
     * Gets all available eggs from every nest
     * @return a list of JsonNodes containing the allocations.
     */
    public static List<JsonNode> getAllEggs() {
        List<JsonNode> eggs = new ArrayList<>();
        for (JsonNode nodes : getNests().get("data")) {
            JsonNode attributes = nodes.get("attributes");
            int id = attributes.get("id").asInt();
            eggs.add(getEggs(id));
        }
        return eggs;
    }

    /**
     * Retries the next available allocation ID
     * @return The ID number
     * @throws NoAvailableAllocationsException If no allocations are available
     */
    public static int getNextAvailableAllocation() {
        for (JsonNode allocation : getAllocations().get("data")) {
            JsonNode attributes = allocation.get("attributes");
            boolean assigned = attributes.get("assigned").asBoolean();
            if(!assigned) return attributes.get("id").asInt();
        }
        throw new NoAvailableAllocationsException();
    }

    /**
     * Starts the server by sending a POST request to the Pterodactyl API.
     *
     * @param serverId the ID of the server to start
     */
    public static void startServer(String serverId) {
        // Define the endpoint to start the server
        String endpoint = "http://panel.slimelabs.net/api/client/servers/" + serverId + "/power";

        // JSON body for starting the server
        String jsonBody = "{\"signal\": \"start\"}";

        // Execute the POST request to start the server
        executePost(endpoint, jsonBody, CLIENT_API_KEY);
    }

    /**
     * Stops the server by sending a POST request to the Pterodactyl API.
     *
     * @param serverId the ID of the server to stop
     */
    public static void stopServer(String serverId) {
        // Define the endpoint to start the server
        String endpoint = "http://panel.slimelabs.net/api/client/servers/" + serverId + "/power";

        // JSON body for starting the server
        String jsonBody = "{\"signal\": \"stop\"}";

        // Execute the POST request to start the server
        executePost(endpoint, jsonBody, CLIENT_API_KEY);
    }

    /**
     * Kills the server by sending a POST request to the Pterodactyl API.
     *
     * @param serverId the ID of the server to kill
     */
    public static void killServer(String serverId) {
        // Define the endpoint to start the server
        String endpoint = "http://panel.slimelabs.net/api/client/servers/" + serverId + "/power";

        // JSON body for starting the server
        String jsonBody = "{\"signal\": \"kill\"}";

        // Execute the POST request to start the server
        executePost(endpoint, jsonBody, CLIENT_API_KEY);
    }

    // Method to get the server ID by its name
    public static String getServerIdByName(String serverName) {
        // Construct the full endpoint to retrieve the servers
        String endpoint = "http://panel.slimelabs.net/api/application/servers";
        JsonNode response = get(endpoint, APPLICATION_API_KEY);

        if (response != null && response.has("data") && response.get("data").isArray()) {
            // Iterate through the server list and find the server by its name
            for (JsonNode server : response.get("data")) {
                if (server.has("attributes") && server.get("attributes").has("name") &&
                        server.get("attributes").get("name").asText().equalsIgnoreCase(serverName)) {
                    // Return the server ID when a match is found
                    return server.get("attributes").get("identifier").asText();
                }
            }
        }
        return null; // Return null if no server with the given name was found
    }

    /**
     * Gets the ip and port given an allocation id
     * @param id the allocation id
     * @return a String array with position 0 containing the ip and position 1 containing the port
     */
    public static String[] getAllocationData(int id) {
        try {
            // Perform GET request and retrieve the response as a JsonNode
            JsonNode rootNode = get("http://panel.slimelabs.net/api/application/nodes/1/allocations", APPLICATION_API_KEY);
            if (rootNode == null) {
                throw new IllegalStateException("Failed to retrieve data from the API.");
            }

            // Navigate to the data array
            JsonNode dataArray = rootNode.get("data");
            if (dataArray == null || !dataArray.isArray()) {
                throw new IllegalArgumentException("Invalid response: 'data' field is missing or not an array.");
            }

            // Iterate through the data array to find the matching allocation ID
            for (JsonNode allocation : dataArray) {
                JsonNode attributes = allocation.get("attributes");
                if (attributes != null && attributes.get("id").asInt() == id) {
                    String ip = attributes.get("ip").asText();
                    String port = attributes.get("port").asText();
                    return new String[]{ip, port};
                }
            }
            throw new IllegalArgumentException("Allocation ID " + id + " not found.");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    /**
     * Gets server data using the server's ID.
     *
     * @param serverId The ID of the server.
     * @return A JSON string representing the server's data, or null if an error occurs.
     */
    public static String getServerData(String serverId) {
        String endpoint = "http://panel.slimelabs.net/api/client/servers/" + serverId;

        // Use the provided GET method to fetch data
        JsonNode response = get(endpoint, CLIENT_API_KEY);

        // Return the JSON as a string if the response is not null
        if (response != null) {
            return response.toString();
        }
        return null;
    }

    /**
     * Gets the state of the server
     * @param id the id of the server
     * @return the state of the server
     */
    public static String getServerState(String id) {
        String endpoint = "http://panel.slimelabs.net/api/client/servers/" + id + "/resources";

        // Get the server status as a JSON object
        JsonNode response = get(endpoint, CLIENT_API_KEY);

        // Check if the response contains the "attributes" field and extract "current_state"
        if (response != null && response.has("attributes")) {
            JsonNode attributes = response.get("attributes");
            if (attributes.has("current_state")) {
                return attributes.get("current_state").asText(); // Get the current_state as a string
            }
        }
        return "Unknown"; // Return Unknown if current_state is not found
    }

    // Static method to perform a GET request and return a JsonNode
    public static JsonNode get(String endpoint, String API_KEY) {
        HttpResponse response = null;
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            // Create the GET request
            HttpGet request = new HttpGet(endpoint);
            request.addHeader("Authorization", "Bearer " + API_KEY);  // Add the API key
            // Execute the request
            response = client.execute(request);
            return objectMapper.readTree(response.getEntity().getContent());
        } catch (Exception e) {
            System.out.println("Response:" + response);
            System.out.println(e.getMessage());
        }
        return null;
    }

    /**
     * Executes a POST request to the given endpoint with the provided JSON body.
     *
     * @param endpoint the target API endpoint
     * @param jsonBody the JSON payload as a String
     * @return the response body as a String
     */
    public static String executePost(String endpoint, String jsonBody, String API_KEY) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            // Create the HTTP POST request
            HttpPost post = new HttpPost(endpoint);
            post.addHeader("Authorization", "Bearer " + API_KEY);
            post.addHeader("Accept", "application/json");
            post.addHeader("Content-Type", "application/json");

            // Set the request body
            post.setEntity(new StringEntity(jsonBody));

            // Execute the request and retrieve the response
            try (CloseableHttpResponse response = client.execute(post)) {
                if(response.getEntity() == null) {
                    return "No content in response";
                }
                return EntityUtils.toString(response.getEntity());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

