package net.slimelabs.sls.api;

/**
 * Contains Endpoint Urls and Keys
 * Warning: This class contains sensitive information do not share contents of this class publicly.
 */
public enum Endpoint {

    APPLICATION_API_URL("https://panel.slimelabs.net"),   // Application Api Endpoint
    CLIENT_API_URL("https://panel.slimelabs.net"),        // Client Api Endpoint
    APPLICATION_API_KEY(""),                     // Application Api key
    CLIENT_API_KEY("");                          // Client Api Key

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
