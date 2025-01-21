package net.slimelabs.sls.api;

/**
 * Contains Endpoint Urls and Keys
 * Warning: This class contains sensitive information do not share contents of this class publicly.
 */
public enum Endpoint {

    APPLICATION_API_URL("http://panel.slimelabs.net"),                       // Application Api Endpoint
    CLIENT_API_URL("http://panel.slimelabs.net"),                            // Client Api Endpoint
    APPLICATION_API_KEY("ptla_A0T0M72ZKXYZd73inutGvT0C8s9U1kn6k3dhGAxIOtT"), // Application Api key
    CLIENT_API_KEY("ptlc_4fhKAdMgAirrXwcmmQJc836CMc0WM1CdKGh2sfPpvzm");      // Client Api Key

    private final String value;

    Endpoint(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
