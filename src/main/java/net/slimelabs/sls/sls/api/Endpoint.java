package net.slimelabs.sls.api;

public enum Endpoint {
    APPLICATION_SERVERS("/application/servers"),
    SERVERS("/servers"),
    NODES("/application/nodes");

    private static final String BASE_URL = "http://panel.slimelabs.net/api";
    private final String path;

    Endpoint(String path) {
        this.path = path;
    }

    public String getUrl() {
        return BASE_URL + path;
    }
}
