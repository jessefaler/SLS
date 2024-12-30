package net.slimelabs.sls;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class SetMount {

    public static void main(String[] args) throws IOException, InterruptedException {
        String baseUrl = "http://panel.slimelabs.net";
        String loginUrl = baseUrl + "/auth/login";
        String csrfCookieUrl = baseUrl + "/sanctum/csrf-cookie";
        String mountUrl = baseUrl + "/admin/servers/view/116/mounts";

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // Step 1: Fetch the login page to extract the initial session cookie and CSRF token
        HttpRequest getLoginPageRequest = HttpRequest.newBuilder()
                .uri(URI.create(loginUrl))
                .GET()
                .build();

        HttpResponse<String> loginPageResponse = client.send(getLoginPageRequest, HttpResponse.BodyHandlers.ofString());
        List<String> initialCookies = loginPageResponse.headers().allValues("set-cookie");
        String initialSessionCookie = extractCookie(initialCookies, "pterodactyl_session");
        String csrfToken = extractCsrfToken(loginPageResponse.body());

        if (csrfToken == null || initialSessionCookie == null) {
            System.err.println("Failed to extract CSRF token or initial session cookie.");
            return;
        }
        System.out.println("Extracted CSRF token: " + csrfToken);
        System.out.println("Initial session cookie: " + initialSessionCookie);

        // Step 2: Obtain the XSRF-TOKEN by making a GET request to /sanctum/csrf-cookie
        HttpRequest getCsrfCookieRequest = HttpRequest.newBuilder()
                .uri(URI.create(csrfCookieUrl))
                .header("Cookie", "pterodactyl_session=" + initialSessionCookie)
                .GET()
                .build();

        HttpResponse<String> csrfCookieResponse = client.send(getCsrfCookieRequest, HttpResponse.BodyHandlers.ofString());
        List<String> csrfCookieHeaders = csrfCookieResponse.headers().allValues("set-cookie");
        String xsrfToken = extractCookie(csrfCookieHeaders, "XSRF-TOKEN");

        if (xsrfToken == null) {
            System.err.println("Failed to retrieve XSRF-TOKEN.");
            return;
        }
        System.out.println("XSRF-TOKEN: " + xsrfToken);

        // Step 3: Log in using username and password
        String username = "admin@slimelabs.net";
        String password = "422364";
        String loginPayload = String.format("{\"user\":\"%s\",\"password\":\"%s\",\"g-recaptcha-response\":\"\"}", username, password);

        HttpRequest loginRequest = HttpRequest.newBuilder()
                .uri(URI.create(loginUrl))
                .header("Content-Type", "application/json")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("X-XSRF-TOKEN", xsrfToken)
                .header("Cookie", String.format("pterodactyl_session=%s; XSRF-TOKEN=%s", initialSessionCookie, xsrfToken))
                .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
                .build();

        HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());

        System.out.println(loginResponse.body());
        List<String> loginSetCookieHeaders = loginResponse.headers().allValues("set-cookie");
        String sessionCookie = extractCookie(loginSetCookieHeaders, "pterodactyl_session");

        if (sessionCookie == null) {
            System.err.println("Failed to retrieve session cookie after login.");
            return;
        }
        System.out.println("Session cookie after login: " + sessionCookie);

        // Step 4: Send POST request to the mount endpoint
        String mountPayload = "_token=" + csrfToken + "&mount_id=4";
        HttpRequest mountRequest = HttpRequest.newBuilder()
                .uri(URI.create(mountUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", "pterodactyl_session=" + sessionCookie)
                .header("Origin", baseUrl)
                .header("Referer", mountUrl)
                .POST(HttpRequest.BodyPublishers.ofString(mountPayload))
                .build();

        HttpResponse<String> mountResponse = client.send(mountRequest, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response Code: " + mountResponse.statusCode());
        System.out.println("Response Body: " + mountResponse.body());
    }

    public static String extractCsrfToken(String html) {
        Document doc = Jsoup.parse(html);
        Element csrfTokenElement = doc.selectFirst("meta[name=csrf-token]");
        return csrfTokenElement != null ? csrfTokenElement.attr("content") : null;
    }

    private static String extractCookie(List<String> cookies, String cookieName) {
        if (cookies == null) return null;
        for (String cookie : cookies) {
            if (cookie.startsWith(cookieName)) {
                String[] parts = cookie.split(";", 2);
                String[] nameValue = parts[0].split("=", 2);
                if (nameValue.length == 2) {
                    return nameValue[1];
                }
            }
        }
        return null;
    }
}