package net.slimelabs.sls.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;

public class UUIDFetcher {

    /**
     * Fetches a players UUID from the mojang users API
     * @param username the name of the player
     * @return The players UUID or an empty Optional
     */
    public static Optional<UUID> getUUIDFromUsername(String username) {
        try {
            String urlString = "https://api.mojang.com/users/profiles/minecraft/" + username;
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Parse the JSON response
                JsonObject jsonObject = JsonParser.parseString(response.toString()).getAsJsonObject();
                String uuidString = jsonObject.get("id").getAsString();

                // Convert the UUID string to a UUID object
                UUID uuid = UUID.fromString(uuidString.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                        "$1-$2-$3-$4-$5"
                ));

                return Optional.of(uuid);
            } else {
                return Optional.empty();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
}
