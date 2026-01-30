package com.protoxon.S4J;

/**
 * Represents the status of a server instance with string values.
 */
public enum ServerStatus {
    UNKNOWN("unknown"),
    OFFLINE("offline"),
    STARTING("starting"),
    RUNNING("running"),
    STOPPING("stopping"),
    PAUSED("paused");

    private final String status;

    // Constructor to set the string value for each status
    ServerStatus(String status) {
        this.status = status;
    }

    // Getter for the string value
    public String getStatus() {
        return status;
    }

    /**
     * Parses a string status value to the corresponding enum.
     * Case-insensitive matching is performed.
     *
     * @param status the status string (e.g., "running", "offline", etc.)
     * @return the corresponding ServerStatus enum, or UNKNOWN if no match is found
     */
    public static ServerStatus fromString(String status) {
        if (status == null || status.isBlank()) {
            return UNKNOWN;
        }

        String normalized = status.trim().toLowerCase();
        for (ServerStatus serverStatus : values()) {
            if (serverStatus.getStatus().equals(normalized)) {
                return serverStatus;
            }
        }
        return UNKNOWN;
    }
}



