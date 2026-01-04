package com.protoxon.S4J.entites;

/**
 * Represents Docker container statistics
 */
public interface DockerContainers {

    /**
     * Gets the total number of containers
     * @return Total number of containers
     */
    int getTotal();

    /**
     * Gets the number of running containers
     * @return Number of running containers
     */
    int getRunning();

    /**
     * Gets the number of paused containers
     * @return Number of paused containers
     */
    int getPaused();

    /**
     * Gets the number of stopped containers
     * @return Number of stopped containers
     */
    int getStopped();

}




