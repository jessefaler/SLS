package net.slimelabs.sls.registries;

import net.slimelabs.sls.World;

import java.util.HashMap;

public class Registry {
    public String name;
    public HashMap<String, World> worlds;
    public Registry(String name, HashMap<String, World> worlds) {
        this.name = name;
        this.worlds = worlds;
    }
    public HashMap<String, World> getWorlds() {
        return worlds;
    }
}
