package com.protoxon.S4J.entities;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public interface Mixin {

    /**
     * Gets the id of the mixin
     * @return String containing the mixin's id
     */
    String getId();

    /**
     * Gets the mixin's description
     * @return Never-null String containing the mixin's description
     */
    String getDescription();

    /**
     * Gets the mixin ids this mixin extends
     * @return Never-null list of parent mixin ids
     */
    List<String> getExtends();

    /**
     * Gets the docker image from the mixin server overlay, if present
     * @return image string, or null if not set
     */
    String getImage();

    /**
     * Gets the server version from the mixin server overlay, if present
     * @return version string, or null if not set
     */
    String getServerVersion();

    /**
     * Gets the server software from the mixin server overlay, if present
     * @return software string, or null if not set
     */
    String getServerSoftware();

    /**
     * Gets the mixin's annotations
     * @return Map containing annotation keys and their associated values
     */
    Map<String, Object> getAnnotations();

    JSONObject getRawJson();

}
