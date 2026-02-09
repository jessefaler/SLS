package com.protoxon.S4J.client.actions;

import com.protoxon.S4J.SLSAction;
import com.protoxon.S4J.client.entities.ClientServer;
import com.protoxon.S4J.client.entities.ConfigPatch;
import com.protoxon.S4J.client.entities.ServerLimits;

import java.util.Map;

public interface ServerCreationAction extends SLSAction<ClientServer> {

    ServerCreationAction setBlueprintId(String id);

    // Returns the id of the blueprint if one has been set
    String getBlueprintId();

    ServerCreationAction setNodeId(String nodeId);

    // Returns the id of the node if one has been set
    String getNodeId();

    /**
     * Sets whether to save the server.
     *
     * @param save True to save the server, false otherwise
     * @return This instance for method chaining
     */
    ServerCreationAction setSave(Boolean save);

    /**
     * Sets the memory limit override in mebibytes.
     *
     * @param memoryLimit The memory limit in mebibytes
     * @return This instance for method chaining
     */
    ServerCreationAction setMemoryLimit(Long memoryLimit);

    /**
     * Sets the swap space override in mebibytes.
     *
     * @param swap The swap space in mebibytes
     * @return This instance for method chaining
     */
    ServerCreationAction setSwap(Long swap);

    /**
     * Sets the IO weight override (relative weight for IO operations, between 10 and 1000).
     *
     * @param ioWeight The IO weight
     * @return This instance for method chaining
     */
    ServerCreationAction setIoWeight(Integer ioWeight);

    /**
     * Sets the CPU limit override as a percentage (e.g., 200% represents two cores).
     *
     * @param cpuLimit The CPU limit percentage
     * @return This instance for method chaining
     */
    ServerCreationAction setCpuLimit(Long cpuLimit);

    /**
     * Sets the disk space limit override in megabytes.
     *
     * @param diskSpace The disk space limit in megabytes
     * @return This instance for method chaining
     */
    ServerCreationAction setDiskSpace(Long diskSpace);

    /**
     * Sets the CPU threads override that can be used by the docker instance.
     *
     * @param threads The threads specification
     * @return This instance for method chaining
     */
    ServerCreationAction setThreads(String threads);

    /**
     * Sets whether the OOM killer should be disabled for this container.
     *
     * @param oomDisabled True to disable OOM killer, false to enable
     * @return This instance for method chaining
     */
    ServerCreationAction setOomDisabled(Boolean oomDisabled);

    /**
     * Sets the complete limits override.
     *
     * @param limits The ServerLimits object containing all limit overrides
     * @return This instance for method chaining
     */
    ServerCreationAction setLimits(ServerLimits limits);

    /**
     * Sets configuration file patch overrides. Keys are file paths (e.g. "server.properties");
     * values define the parser and find/replace map. These are applied after software and
     * blueprint configs (same key is overridden, new keys are merged).
     *
     * @param configs Map of file path to config patch, or null to clear
     * @return This instance for method chaining
     */
    ServerCreationAction setConfigOverrides(Map<String, ConfigPatch> configs);

    /**
     * Overrides the blueprint's server software (registry id used for invocation and path).
     *
     * @param software The software id, or null to use the blueprint value
     * @return This instance for method chaining
     */
    ServerCreationAction setSoftware(String software);

    /**
     * Overrides the blueprint's server version (path segment, e.g. "1.0.0").
     *
     * @param version The version string, or null to use the blueprint value
     * @return This instance for method chaining
     */
    ServerCreationAction setVersion(String version);

    /**
     * Returns the version override if one has been set, otherwise an empty string.
     */
    String getVersion();

    /**
     * Overrides the blueprint's container image.
     *
     * @param image The container image (e.g. "sls:java_21"), or null to use the blueprint value
     * @return This instance for method chaining
     */
    ServerCreationAction setImage(String image);

}
