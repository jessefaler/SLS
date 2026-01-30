package com.protoxon.S4J.entities;

/**
 * Represents Docker storage information
 */
public interface DockerStorage {

    /**
     * Gets the storage driver
     * @return String containing the driver name
     */
    String getDriver();

    /**
     * Gets the filesystem type
     * @return String containing the filesystem type
     */
    String getFilesystem();

}





