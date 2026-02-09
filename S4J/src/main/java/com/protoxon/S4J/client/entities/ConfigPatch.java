package com.protoxon.S4J.client.entities;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a configuration file patch applied at server creation.
 * Patches are applied after software and blueprint configs; same key is overridden, new keys are merged.
 * <p>
 * The {@code find} map is key-value pairs: keys are the config key to match (e.g. "motd" for properties),
 * values are the replacement (String, Number, or Boolean).
 */
public class ConfigPatch {

    private final String parser;
    private final Map<String, Object> find;

    public ConfigPatch(@NotNull String parser, @NotNull Map<String, Object> find) {
        this.parser = parser;
        this.find = find == null ? Collections.emptyMap() : Collections.unmodifiableMap(find);
    }

    /**
     * Parser type for the config file (e.g. "properties", "json").
     */
    @NotNull
    public String getParser() {
        return parser;
    }

    /**
     * Key-value replacements: key is the config key to find, value is the replacement.
     */
    @NotNull
    public Map<String, Object> getFind() {
        return find;
    }
}
