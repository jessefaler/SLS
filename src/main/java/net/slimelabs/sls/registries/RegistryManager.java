package net.slimelabs.sls.registries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class RegistryManager {
    public HashMap<String, Registry> REGISTRIES = new HashMap<>();
    public void addRegistry(String name, Registry registry) {
        REGISTRIES.put(name, registry);
    }

    /**
     * Gets all registry names
     * @return A string array of the names
     */
    public String[] getRegistryNames() {
        return REGISTRIES.keySet().toArray(new String[0]);
    }

    /**
     * deletes a registry from memory
     */
    public void deleteRegistry(String name) {
        REGISTRIES.remove(name);
    }

    /**
     * deletes all registries in memory
     */
    public void purgeRegistries() {
        REGISTRIES.clear();
    }


}
