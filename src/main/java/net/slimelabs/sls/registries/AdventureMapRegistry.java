package net.slimelabs.sls.registries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdventureMapRegistry {

    Map<String, AdventureConfig> AdventureMapRegistry = new HashMap<>();

    //adds a new AdventureMap object to the registry
    public void addAdventure(String name, String authors, int minPlayers, int maxPlayers, int ram, boolean reset, boolean useCustomJDK, String customJDKPath, String filePath, String MinigameDescription) {
        AdventureMapRegistry.put(name, new AdventureMapRegistry.AdventureConfig(authors, minPlayers, maxPlayers, ram, reset, useCustomJDK, customJDKPath, filePath, MinigameDescription));
    }

    public void purgeRegistry() {
        AdventureMapRegistry.clear();
    }

    //returns authors
    public String getAuthors(String name) {
        return AdventureMapRegistry.get(name).authors;
    }

    //returns the minimum players a Adventure Map can have
    public int getMinPlayers(String name) {
        return AdventureMapRegistry.get(name).minPlayers;
    }

    //returns max players a Adventure Map can have
    public int getMaxPlayers(String name) {
        return AdventureMapRegistry.get(name).maxPlayers;
    }

    //returns custom ram
    public int getCustomRam(String name) {
        return AdventureMapRegistry.get(name).ramMB;
    }

    public boolean getUseCustomJDK(String name) {
        return AdventureMapRegistry.get(name).useCustomJDK;
    }

    public String getCustomJDKPath(String name) {
        return AdventureMapRegistry.get(name).customJDKPath;
    }

    public boolean getReset(String name) {
        return AdventureMapRegistry.get(name).reset;
    }

    //returns the folder name where the Adventure Map server is located
    public String getFolderName(String name) {
        return AdventureMapRegistry.get(name).FolderName;
    }

    //returns the description for the Adventure Map
    public String getAdventureDescription(String name) {
        return AdventureMapRegistry.get(name).FolderName;
    }

    public String toString() {
        StringBuilder output = new StringBuilder("§7Adventure Map registry: ");
        for(String name : AdventureMapRegistry.keySet()) {
            output.append("\n§a - name: ").append(name);
            output.append("\n§3   Authors: §c").append(AdventureMapRegistry.get(name).authors);
            output.append("\n§3   max-players: §6").append(AdventureMapRegistry.get(name).maxPlayers);
            output.append("\n§3   min-players: §6").append(AdventureMapRegistry.get(name).minPlayers);
            output.append("\n§3   ram-allocation: §c").append(AdventureMapRegistry.get(name).ramMB).append("mb");
            output.append("\n§3   reset-world: §c").append(AdventureMapRegistry.get(name).reset);
            output.append("\n§3   use-custom-java-version: §c").append(AdventureMapRegistry.get(name).useCustomJDK);
            output.append("\n§3   custom-java-version-path: §c").append(AdventureMapRegistry.get(name).customJDKPath);
            output.append("\n§3   server-folder-path: §c").append(AdventureMapRegistry.get(name).FolderName);
            String description = AdventureMapRegistry.get(name).AdventureMapDescription;
            if(description.length() > 33) {//shorten the description to 15 characters, so it doesn't take up the screen when viewed
                description = description.substring(0, 33).trim() + "...";
            }
            output.append("\n§3   description: §c").append(description);
        }
        return output.toString();
    }

    //returns a string with a single Adventure Map's config.
    public String viewAAdventureMapsConfig(String name) {
        if(!containsAdventure(name)) {
            return "§c" + name + " dose not exist.";
        }

        return "§7config for " + name + "\n§a - name: " + name +
                "\n§3   Authors: §c" + AdventureMapRegistry.get(name).authors +
                "\n§3   max-players: §6" + AdventureMapRegistry.get(name).maxPlayers +
                "\n§3   min-players: §6" + AdventureMapRegistry.get(name).minPlayers +
                "\n§3   ram-allocation: §c" + AdventureMapRegistry.get(name).ramMB + "mb" +
                "\n§3   reset-world: §6" + AdventureMapRegistry.get(name).reset +
                "\n§3   use-custom-java-version: §6" + AdventureMapRegistry.get(name).useCustomJDK +
                "\n§3   custom-java-version-path: §6" + AdventureMapRegistry.get(name).customJDKPath +
                "\n§3   server-folder-path: §c" + AdventureMapRegistry.get(name).FolderName +
                "\n§3   description: §c" + AdventureMapRegistry.get(name).AdventureMapDescription;
    }

    //checks if a Adventure Maps name (key) is in the Map. Also ignores the casing
    public boolean containsAdventure(String name) {
        for(String key : AdventureMapRegistry.keySet()) {
            if(key.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    //returns arraylist of Adventure Maps
    public List<String> getKeys() {//gets all keys
        ArrayList<String> listOfKeys = new ArrayList<>();
        for(String key : AdventureMapRegistry.keySet()) {
            listOfKeys.add(key.trim().replace(" ", "_").toLowerCase());
        }
        return listOfKeys;
    }

    /*
     * creates AdventureConfigObjects these store the configuration of Adventure Maps such as:
     *  The max players it can have, the minimum players it must have to start/stay playing, how much ram the server the game runs on should have
     *  the name of the server folder where the game is located
     * A record in java is a class that is meant to only store data it automatically provides the getter and setter methods.
     */
    private record AdventureConfig(String authors, int minPlayers, int maxPlayers, int ramMB, boolean reset, boolean useCustomJDK, String customJDKPath, String FolderName, String AdventureMapDescription) {}
}
