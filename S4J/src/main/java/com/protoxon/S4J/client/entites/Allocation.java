package com.protoxon.S4J.client.entites;

import java.util.List;
import java.util.Map;

/**
 * Represents the allocation configuration for a server.
 */
public interface Allocation {

    /**
     * Indicates if the server should use a dedicated outgoing IP.
     *
     * @return true if a dedicated outgoing IP should be used, false otherwise
     */
    boolean isForceOutgoingIp();

    /**
     * The alias set for this allocation, if any.
     *
     * @return the allocation alias, or null if not set
     */
    String getAlias();

    /**
     * The default IP that should be used for this server.
     *
     * @return the default IP address, or null if not set
     */
    String getIp();

    /**
     * The default port that should be used for this server.
     *
     * @return the default port, or 0 if not set
     */
    int getPort();

    /**
     * All allocation mappings for this server.
     * The map key is the IP, the value is the list of ports assigned to that IP.
     *
     * @return an unmodifiable map of allocation mappings
     */
    Map<String, List<Integer>> getMappings();
}

