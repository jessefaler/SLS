package com.protoxon.S4J.client.entities;


import com.protoxon.S4J.InstallInfo;
import com.protoxon.S4J.PowerAction;
import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.ServerStats;
import com.protoxon.S4J.ServerStatus;
import com.protoxon.S4J.requests.PaginationAction;

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

    /**
     * The blueprint id the server is using
     *
     * @return Possibly-null String containing the Server's id.
     */
    String getBlueprintId();

    /**
     * The id of the node this server is running on
     *
     * @return Possibly-null String containing the node id.
     */
    String getNodeId();

    /**
     * The name of the node this server is running on
     *
     * @return Possibly-null String containing the node name.
     */
    String getNodeName();

    /**
     * Retrieves the allocation information for this server.
     *
     * @return the server allocation, or null if not available
     */
    Allocation getAllocation();

    /**
     * Returns the overrides that were set when this server was created (save, limits, configs,
     * software, version, image). Null if no overrides were set.
     *
     * @return the server creation overrides, or null
     */
    ServerOverrides getOverrides();

    /**
     * The software id this server is running (from configuration).
     *
     * @return the software id, or null if not set
     */
    String getSoftwareId();

    /**
     * The software version this server is running (from configuration).
     *
     * @return the software version, or null if not set
     */
    String getSoftwareVersion();

    /**
     * The container image this server is using.
     *
     * @return the image, or null if not set
     */
    String getImage();

    /**
     * The effective resource limits for this server.
     *
     * @return the limits, or null if not set
     */
    ServerLimits getLimits();

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
     * Retrieves the servers stats with optional disk usage cache update
     * @param update if true, updates the overlay usage cache and disk usage cache before returning stats
     * @return ServerStats
     */
    SLSAction<ServerStats> getStats(boolean update);

    /**
     * Retrieves the servers ip
     * @return the servers ip
     */
    String getIp();

    /**
     * Deletes the server
     */
    SLSAction<Void> delete();

    /**
     * Deletes the server with optional force flag
     * @param force if true, ensures the server is cleaned up on Protocube even if deletion from the daemon fails
     */
    SLSAction<Void> delete(boolean force);

    /**
     * Retrieves the servers port
     * @return the servers port number
     */
    int getPort();

    default SLSAction<Void> stop() {
        return setPower(PowerAction.STOP);
    }

    default SLSAction<Void> start() {
        return setPower(PowerAction.START);
    }

    default SLSAction<Void> restart() {
        return setPower(PowerAction.RESTART);
    }

    default SLSAction<Void> kill() {
        return setPower(PowerAction.KILL);
    }

    default SLSAction<Void> pause() {
        return setPower(PowerAction.PAUSE);
    }

    default SLSAction<Void> unpause() {
        return setPower(PowerAction.UNPAUSE);
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

    /**
     * Retrieves server logs with pagination. Page 1 contains the most recent lines.
     *
     * @return PaginationAction that returns a page of log lines
     */
    PaginationAction<String> getLogs();

    /**
     * Retrieves server logs with a custom page size. Page 1 contains the most recent lines.
     *
     * @param perPage The number of log lines per page (defaults to 50, max 100, min 1)
     * @return PaginationAction that returns a page of log lines
     */
    default PaginationAction<String> getLogs(int perPage) {
        int size = perPage <= 0 ? 50 : Math.min(perPage, 100);
        return getLogs().limit(size);
    }

    /**
     * Retrieves the current installation state for this server.
     *
     * @return SLSAction that resolves to the install info
     */
    SLSAction<InstallInfo> getInstallInfo();

    /**
     * Retrieves install logs from all install phases (install, warmup, post-warmup) with pagination.
     * Page 1 contains the most recent lines.
     *
     * @return PaginationAction that returns a page of install log lines
     */
    PaginationAction<String> getInstallLogs();

    /**
     * Retrieves install logs with a custom page size. Page 1 contains the most recent lines.
     *
     * @param perPage The number of log lines per page (defaults to 50, max 100, min 1)
     * @return PaginationAction that returns a page of install log lines
     */
    default PaginationAction<String> getInstallLogs(int perPage) {
        int size = perPage <= 0 ? 50 : Math.min(perPage, 100);
        return getInstallLogs().limit(size);
    }

    /**
     * Reinstalls the server software using its configured installation script.
     * Every server using the same installed artifact must be stopped first.
     *
     * @return SLSAction that completes when the reinstall request is accepted
     */
    SLSAction<Void> reinstall();

    /**
     * Resets the server by deleting the overlay filesystem and restarting if it was running.
     * This will stop the server if it's running, wait for it to fully stop, reset the overlay,
     * and then start it back up if it was running before.
     * @return SLSAction that completes when the reset request is accepted
     */
    SLSAction<Void> reset();

}
