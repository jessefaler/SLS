package com.protoxon.S4J;

/**
 * Represents the current phase of a server installation.
 */
public enum InstallPhase {
    UNKNOWN("unknown"),
    IDLE("idle"),
    INSTALLING("installing"),
    WARMING("warming"),
    POST_WARMUP("post_warmup"),
    INSTALL_FAILED("install_failed"),
    WARMUP_FAILED("warmup_failed"),
    POST_WARMUP_FAILED("post_warmup_failed"),
    COMPLETED("completed");

    private final String phase;

    InstallPhase(String phase) {
        this.phase = phase;
    }

    public String getPhase() {
        return phase;
    }

    /**
     * Parses a string phase value to the corresponding enum.
     * Case-insensitive matching is performed.
     *
     * @param phase the phase string (e.g., "installing", "completed")
     * @return the corresponding InstallPhase enum, or UNKNOWN if no match is found
     */
    public static InstallPhase fromString(String phase) {
        if (phase == null || phase.isBlank()) {
            return UNKNOWN;
        }

        String normalized = phase.trim().toLowerCase();
        for (InstallPhase installPhase : values()) {
            if (installPhase.getPhase().equals(normalized)) {
                return installPhase;
            }
        }
        return UNKNOWN;
    }
}
