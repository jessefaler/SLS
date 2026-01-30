package com.protoxon.S4J.client.entities.impl;

import com.protoxon.S4J.client.entities.Allocation;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link Allocation} backed by a JSON object.
 */
public class AllocationImpl implements Allocation {

    private final JSONObject json;

    public AllocationImpl(JSONObject json) {
        this.json = json;
    }

    @Override
    public boolean isForceOutgoingIp() {
        return json.optBoolean("force_outgoing_ip", false);
    }

    @Override
    public String getAlias() {
        return json.optString("alias", "");
    }

    @Override
    public String getIp() {
        JSONObject def = json.optJSONObject("default");
        if (def == null) {
            return null;
        }
        return def.optString("ip", "");
    }

    @Override
    public int getPort() {
        JSONObject def = json.optJSONObject("default");
        if (def == null) {
            return 0;
        }
        return def.optInt("port", 0);
    }

    @Override
    public Map<String, List<Integer>> getMappings() {
        JSONObject mappingsObj = json.optJSONObject("mappings");
        if (mappingsObj == null) {
            return Collections.emptyMap();
        }

        Map<String, List<Integer>> mappings = new HashMap<>();

        for (String key : mappingsObj.keySet()) {
            JSONArray array = mappingsObj.optJSONArray(key);
            if (array == null) {
                continue;
            }

            List<Integer> ports = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                ports.add(array.optInt(i));
            }

            mappings.put(key, Collections.unmodifiableList(ports));
        }

        return Collections.unmodifiableMap(mappings);
    }
}

