package com.protoxon.S4J.entities;

/**
 * Represents Docker cgroups information
 */
public interface DockerCgroups {

    /**
     * Gets the cgroups driver
     * @return String containing the driver name
     */
    String getDriver();

    /**
     * Gets the cgroups version
     * @return String containing the version
     */
    String getVersion();

}





