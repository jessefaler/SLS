package net.slimelabs.sls.registries;

import java.util.HashMap;

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

    /**
     * Gets a registry given its name
     * @param name the registry's name
     * @return the registry with the given name or
     * null if there is no registry with the given name
     */
    public Registry getRegistry(String name) {
        return REGISTRIES.get(name);
    }

    /**
     * Checks if a registry with the given name exists
     * @param name the registries name
     * @return true if the registry is present
     */
    public boolean doseRegistryExist(String name) {
        return REGISTRIES.containsKey(name);
    }

    public boolean doseWorldExist(String world) {
        for(Registry registry : REGISTRIES.values()) {
            for(String name : registry.getWorlds().keySet()) {
                if(name.equals(world)) {
                    return true;
                }
            }
        }
        return false;
    }

}
