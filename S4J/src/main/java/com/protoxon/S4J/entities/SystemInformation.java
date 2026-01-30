package com.protoxon.S4J.entities;

import com.protoxon.S4J.DataType;
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
     * Formats the memory in the specified data type.
     *
     * @param dataType the data type to format the memory in
     * @return a formatted string representing the memory
     */
    String getMemoryFormatted(DataType dataType);

    /**
     * Automatically formats the memory in the most appropriate unit (KB, MB, GB, or TB)
     * based on the size, similar to Docker's format (e.g., "612.2 MB" or "4.4 GB").
     *
     * @return a formatted string representing the memory with automatically selected unit
     */
    String getMemoryFormattedAuto();

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
     * Gets the Docker information
     * @return DockerInformation containing Docker-related information, or null if not available
     */
    DockerInformation getDocker();

    /**
     * Gets the raw JSON object
     * @return JSONObject containing all system information
     */
    JSONObject getRawJson();
}
