package com.protoxon.S4J.client.entities;

import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Overrides that were set when a server was created. Returned with get-server and list-servers.
 * All fields are optional; only set overrides are non-null.
 */
public class ServerOverrides {

    private final Boolean save;
    private final ServerLimits limits;
    private final Map<String, ConfigPatch> configs;
    private final String software;
    private final String version;
    private final String image;

    public ServerOverrides(Boolean save, ServerLimits limits, Map<String, ConfigPatch> configs,
                           String software, String version, String image) {
        this.save = save;
        this.limits = limits;
        this.configs = configs == null ? null : Collections.unmodifiableMap(new HashMap<>(configs));
        this.software = software;
        this.version = version;
        this.image = image;
    }

    public Boolean getSave() {
        return save;
    }

    public ServerLimits getLimits() {
        return limits;
    }

    public Map<String, ConfigPatch> getConfigs() {
        return configs;
    }

    public String getSoftware() {
        return software;
    }

    public String getVersion() {
        return version;
    }

    public String getImage() {
        return image;
    }

    /**
     * Parses overrides from a JSON object (e.g. the "overrides" key in a server response).
     *
     * @param json The overrides JSON, or null
     * @return A ServerOverrides instance, or null if json is null or empty
     */
    public static ServerOverrides fromJson(JSONObject json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        Boolean save = json.has("save") ? json.optBoolean("save") : null;
        ServerLimits limits = ServerLimits.fromJson(json.optJSONObject("limits"));
        Map<String, ConfigPatch> configs = parseConfigs(json.optJSONObject("configs"));
        String software = json.optString("software", null);
        if (software != null && software.isEmpty()) {
            software = null;
        }
        String version = json.optString("version", null);
        if (version != null && version.isEmpty()) {
            version = null;
        }
        String image = json.optString("image", null);
        if (image != null && image.isEmpty()) {
            image = null;
        }
        if (save == null && limits == null && (configs == null || configs.isEmpty())
                && software == null && version == null && image == null) {
            return null;
        }
        return new ServerOverrides(save, limits, configs, software, version, image);
    }

    private static Map<String, ConfigPatch> parseConfigs(JSONObject configsObj) {
        if (configsObj == null || configsObj.isEmpty()) {
            return null;
        }
        Map<String, ConfigPatch> map = new HashMap<>();
        for (String fileName : configsObj.keySet()) {
            JSONObject patchObj = configsObj.optJSONObject(fileName);
            if (patchObj != null) {
                String parser = patchObj.optString("parser", "properties");
                JSONObject findObj = patchObj.optJSONObject("find");
                Map<String, Object> find = findObj != null ? toMap(findObj) : Collections.emptyMap();
                map.put(fileName, new ConfigPatch(parser, find));
            }
        }
        return map;
    }

    private static Map<String, Object> toMap(JSONObject json) {
        Map<String, Object> map = new HashMap<>();
        for (String key : json.keySet()) {
            Object value = json.opt(key);
            if (value != null) {
                map.put(key, value);
            }
        }
        return map;
    }
}
