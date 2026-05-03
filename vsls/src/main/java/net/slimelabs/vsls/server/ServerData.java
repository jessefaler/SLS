package net.slimelabs.vsls.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerData {
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    public <T> void set(String key, T value) {
        data.put(key, value);
    }

    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    public void remove(String key) {
        data.remove(key);
    }
}
