package com.protoxon.S4J.entites;

import org.json.JSONObject;

import java.util.Map;

public interface Blueprint {

    /**
     * Gets the id of the blueprint
     * @return String containing the blueprints id
     */
    String getId();

    /**
     * Gets the blueprints name
     * @return Never-null String containing the blueprints name
     */
    String getName();

    /**
     * Gets the servers version from the blueprint
     * @return Never-null String containing the server version
     */
    String getServerVersion();

    /**
     * Gets the servers software from the blueprint
     * @return Never-null String containing the server software
     */
    String getServerSoftware();

    /**
     * Gets the blueprints type
     * @return Never-null String containing the blueprints type
     */
    String getType();

    /**
     * Gets the blueprints annotations
     * @return Map containing annotation keys and their associated values
     */
    Map<String, Object> getAnnotations();

    JSONObject getRawJson();

}
