package com.protoxon.S4J.client.entites;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.entites.SystemInformation;

/**
 * Represents a node instance
 */
public interface ClientNode {

    /**
     * The id of the Node
     *
     * @return Possibly-null String containing the Node's id.
     */
    String getId();

    /**
     * The name of the Node
     *
     * @return Possibly-null String containing the Node's name.
     */
    String getName();

    /**
     * The location of the Node
     *
     * @return Possibly-null String containing the Node's location.
     */
    String getLocation();

    /**
     * The URL of the Node
     *
     * @return Possibly-null String containing the Node's URL.
     */
    String getUrl();

    /**
     * Retrieves the node's system information
     * @return SLSAction that returns SystemInformation
     */
    SLSAction<SystemInformation> getSystemInformation();

}

