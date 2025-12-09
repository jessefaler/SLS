package com.protoxon.S4J.client.entites;


import com.protoxon.S4J.PowerAction;
import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.ServerStats;
import com.protoxon.S4J.ServerStatus;

import java.util.List;

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

    /**
     * Retrieves the servers status
     * @return ServerStatus
     */
    SLSAction<ServerStats> getStats();

    /**
     * Retrieves the servers ip
     * @return the servers ip
     */
    String getIp();

    /**
     * Retrieves the servers port
     * @return the servers port number
     */
    int getPort();

    default SLSAction<Void> stop() {
        return setPower(PowerAction.STOP);
    }

    default SLSAction<Void> kill() {
        return setPower(PowerAction.KILL);
    }

    /**
     * Sends a single command to the server
     * @param command The command to send
     * @return SLSAction that completes when the command is sent
     */
    SLSAction<Void> sendCommand(String command);

    /**
     * Sends multiple commands to the server
     * @param commands The commands to send
     * @return SLSAction that completes when the commands are sent
     */
    SLSAction<Void> sendCommands(String... commands);

    /**
     * Sends multiple commands to the server
     * @param commands The list of commands to send
     * @return SLSAction that completes when the commands are sent
     */
    SLSAction<Void> sendCommands(List<String> commands);

}
