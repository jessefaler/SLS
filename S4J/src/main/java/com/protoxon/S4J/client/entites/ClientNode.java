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
     * The drained state of the Node
     *
     * @return Boolean indicating whether the node is drained.
     */
    boolean getDrained();

    /**
     * Retrieves the node's system information
     * @return SLSAction that returns SystemInformation
     */
    SLSAction<SystemInformation> getSystemInformation();

    /**
     * Sets the drained state of the node
     * @param drained Whether the node should be drained
     * @return SLSAction that returns Void
     */
    SLSAction<Void> setDrained(boolean drained);

}

