package com.protoxon.S4J.entities;

/**
 * Represents Docker information from the protocube API
 */
public interface DockerInformation {

    /**
     * Gets the Docker version
     * @return String containing the Docker version
     */
    String getVersion();

    /**
     * Gets the Docker cgroups information
     * @return DockerCgroups containing cgroups information
     */
    DockerCgroups getCgroups();

    /**
     * Gets the Docker containers information
     * @return DockerContainers containing container statistics
     */
    DockerContainers getContainers();

    /**
     * Gets the Docker storage information
     * @return DockerStorage containing storage information
     */
    DockerStorage getStorage();

    /**
     * Gets the Docker runc information
     * @return DockerRunc containing runc information
     */
    DockerRunc getRunc();

}





