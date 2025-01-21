package net.slimelabs.sls.registries;

import net.slimelabs.sls.server.ServerConfiguration;

import java.nio.file.Path;
import java.util.HashMap;

public class Registry {
    public String name;
    public HashMap<String, ServerConfiguration> worlds;
    public Path path;
    public Registry(String name, HashMap<String, ServerConfiguration> worlds, Path path) {
        this.name = name;
        this.worlds = worlds;
        this.path = path;
    }
    public HashMap<String, ServerConfiguration> getWorlds() {
        return worlds;
    }

    public String[] getWorldNames() {
        return worlds.keySet().toArray(new String[0]);
    }

    public boolean isWorldPresent(String world) {
        return worlds.containsKey(world);
    }

    /**
     * Gets a world given its name
     * @return The world with the specified name
     * or null if none were found
     */
    public ServerConfiguration getWorld(String name) {
        return worlds.get(name);
    }
}
