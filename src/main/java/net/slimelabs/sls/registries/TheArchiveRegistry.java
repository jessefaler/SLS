package net.slimelabs.sls.registries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TheArchiveRegistry {
    Map<String, TheArchiveConfig> TheArchiveRegistry = new HashMap<>();

    //adds a new Archive object to the registry
    public void addArchive(String name, int ram, boolean reset, boolean useCustomJDK, String customJDKPath, String filePath) {
        TheArchiveRegistry.put(name, new TheArchiveRegistry.TheArchiveConfig(ram, reset, useCustomJDK, customJDKPath, filePath));
    }

    public void purgeRegistry() {
        TheArchiveRegistry.clear();
    }

    //returns custom ram
    public int getCustomRam(String name) {
        return TheArchiveRegistry.get(name).ramMB;
    }

    public boolean getUseCustomJDK(String name) {
        return TheArchiveRegistry.get(name).useCustomJDK;
    }

    public String getCustomJDKPath(String name) {
        return TheArchiveRegistry.get(name).customJDKPath;
    }

    public boolean getReset(String name) {
        return TheArchiveRegistry.get(name).reset;
    }

    //returns the folder name where the Archive server is located
    public String getFolderName(String name) {
        return TheArchiveRegistry.get(name).FolderName;
    }

    public String toString() {
        StringBuilder output = new StringBuilder("§7Archive registry: ");
        for(String name : TheArchiveRegistry.keySet()) {
            output.append("\n§a - name: ").append(name);
            output.append("\n§3   ram-allocation: §c").append(TheArchiveRegistry.get(name).ramMB).append("mb");
            output.append("\n§3   reset-world: §c").append(TheArchiveRegistry.get(name).reset);
            output.append("\n§3   use-custom-java-version: §c").append(TheArchiveRegistry.get(name).useCustomJDK);
            output.append("\n§3   custom-java-version-path: §c").append(TheArchiveRegistry.get(name).customJDKPath);
            output.append("\n§3   server-folder-path: §c").append(TheArchiveRegistry.get(name).FolderName);
        }
        return output.toString();
    }

    //returns a string with a single Archives config.
    public String viewAArchiveConfig(String name) {
        if(!containsArchiveMap(name)) {
            return "§c" + name + " dose not exist.";
        }

        return "§7config for " + name + "\n§a - name: " + name +
                "\n§3   ram-allocation: §c" + TheArchiveRegistry.get(name).ramMB + "mb" +
                "\n§3   reset-world: §6" + TheArchiveRegistry.get(name).reset +
                "\n§3   use-custom-java-version: §6" + TheArchiveRegistry.get(name).useCustomJDK +
                "\n§3   custom-java-version-path: §6" + TheArchiveRegistry.get(name).customJDKPath +
                "\n§3   server-folder-path: §c" + TheArchiveRegistry.get(name).FolderName;
    }

    //checks if a Archive's name (key) is in the Map. Also ignores the casing
    public boolean containsArchiveMap(String name) {
        for(String key : TheArchiveRegistry.keySet()) {
            if(key.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    //returns arraylist of Archives
    public List<String> getKeys() {//gets all keys
        ArrayList<String> listOfKeys = new ArrayList<>();
        for(String key : TheArchiveRegistry.keySet()) {
            listOfKeys.add(key.trim().replace(" ", "_").toLowerCase());
        }
        return listOfKeys;
    }

    /*
     * creates ArchiveConfigObjects these store the configuration of Archives such as:
     *  The max players it can have, the minimum players it must have to start/stay playing, how much ram the server the game runs on should have
     *  the name of the server folder where the game is located
     * A record in java is a class that is meant to only store data it automatically provides the getter and setter methods.
     */
    private record TheArchiveConfig(int ramMB, boolean reset, boolean useCustomJDK, String customJDKPath, String FolderName) {}
}
