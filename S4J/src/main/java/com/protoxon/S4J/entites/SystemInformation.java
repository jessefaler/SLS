package com.protoxon.S4J.entites;

import org.json.JSONObject;

/**
 * Represents system information from the protocube API
 */
public interface SystemInformation {

    /**
     * Gets the protocube version
     * @return String containing the version
     */
    String getVersion();

    /**
     * Gets the system architecture
     * @return String containing the architecture
     */
    String getArchitecture();

    /**
     * Gets the number of CPU threads
     * @return Number of CPU threads
     */
    int getCpuThreads();

    /**
     * Gets the total system memory in bytes
     * @return Total memory in bytes, or -1 if not available
     */
    long getMemoryBytes();

    /**
     * Gets the kernel version
     * @return String containing the kernel version
     */
    String getKernelVersion();

    /**
     * Gets the operating system name
     * @return String containing the OS name
     */
    String getOs();

    /**
     * Gets the operating system type
     * @return String containing the OS type
     */
    String getOsType();

    /**
     * Gets the raw JSON object
     * @return JSONObject containing all system information
     */
    JSONObject getRawJson();
}
