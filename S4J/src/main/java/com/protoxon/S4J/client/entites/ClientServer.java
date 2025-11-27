package com.protoxon.S4J.client.entites;


import com.protoxon.S4J.PowerAction;
import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.ServerStatus;

import java.util.function.Consumer;

/**
 * Represents a server instance
 */
public interface ClientServer {

    /**
     * The id of the Server
     *
     * @return Possibly-null String containing the Server's id.
     */
    String getId();

    SLSAction<Void> setPower(PowerAction powerAction);

    /**
     * Retrieves the servers status
     * @return ServerStatus
     */
    SLSAction<ServerStatus> getStatus();

    default SLSAction<Void> stop() {
        return setPower(PowerAction.STOP);
    }

    default SLSAction<Void> kill() {
        return setPower(PowerAction.KILL);
    }

}
