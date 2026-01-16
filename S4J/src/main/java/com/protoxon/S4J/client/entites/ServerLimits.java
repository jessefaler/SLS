package com.protoxon.S4J.client.entites;

/**
 * Represents resource limits for a server instance.
 * All fields are optional and can be set individually.
 */
public class ServerLimits {

    private Long memoryLimit;
    private Long swap;
    private Integer ioWeight;
    private Long cpuLimit;
    private Long diskSpace;
    private String threads;
    private Boolean oomDisabled;

    public ServerLimits() {
    }

    /**
     * Gets the memory limit in mebibytes.
     *
     * @return The memory limit, or null if not set
     */
    public Long getMemoryLimit() {
        return memoryLimit;
    }

    /**
     * Sets the memory limit in mebibytes.
     *
     * @param memoryLimit The memory limit in mebibytes
     * @return This instance for method chaining
     */
    public ServerLimits setMemoryLimit(Long memoryLimit) {
        this.memoryLimit = memoryLimit;
        return this;
    }

    /**
     * Gets the swap space in mebibytes.
     *
     * @return The swap space, or null if not set
     */
    public Long getSwap() {
        return swap;
    }

    /**
     * Sets the swap space in mebibytes.
     *
     * @param swap The swap space in mebibytes
     * @return This instance for method chaining
     */
    public ServerLimits setSwap(Long swap) {
        this.swap = swap;
        return this;
    }

    /**
     * Gets the IO weight (relative weight for IO operations, between 10 and 1000).
     *
     * @return The IO weight, or null if not set
     */
    public Integer getIoWeight() {
        return ioWeight;
    }

    /**
     * Sets the IO weight (relative weight for IO operations, between 10 and 1000).
     *
     * @param ioWeight The IO weight
     * @return This instance for method chaining
     */
    public ServerLimits setIoWeight(Integer ioWeight) {
        this.ioWeight = ioWeight;
        return this;
    }

    /**
     * Gets the CPU limit as a percentage (e.g., 200% represents two cores).
     *
     * @return The CPU limit, or null if not set
     */
    public Long getCpuLimit() {
        return cpuLimit;
    }

    /**
     * Sets the CPU limit as a percentage (e.g., 200% represents two cores).
     *
     * @param cpuLimit The CPU limit percentage
     * @return This instance for method chaining
     */
    public ServerLimits setCpuLimit(Long cpuLimit) {
        this.cpuLimit = cpuLimit;
        return this;
    }

    /**
     * Gets the disk space limit in megabytes.
     *
     * @return The disk space limit, or null if not set
     */
    public Long getDiskSpace() {
        return diskSpace;
    }

    /**
     * Sets the disk space limit in megabytes.
     *
     * @param diskSpace The disk space limit in megabytes
     * @return This instance for method chaining
     */
    public ServerLimits setDiskSpace(Long diskSpace) {
        this.diskSpace = diskSpace;
        return this;
    }

    /**
     * Gets the CPU threads that can be used by the docker instance.
     *
     * @return The threads specification, or null if not set
     */
    public String getThreads() {
        return threads;
    }

    /**
     * Sets the CPU threads that can be used by the docker instance.
     *
     * @param threads The threads specification
     * @return This instance for method chaining
     */
    public ServerLimits setThreads(String threads) {
        this.threads = threads;
        return this;
    }

    /**
     * Gets whether the OOM killer is disabled for this container.
     *
     * @return True if OOM killer is disabled, false if enabled, or null if not set
     */
    public Boolean getOomDisabled() {
        return oomDisabled;
    }

    /**
     * Sets whether the OOM killer should be disabled for this container.
     *
     * @param oomDisabled True to disable OOM killer, false to enable
     * @return This instance for method chaining
     */
    public ServerLimits setOomDisabled(Boolean oomDisabled) {
        this.oomDisabled = oomDisabled;
        return this;
    }

}
