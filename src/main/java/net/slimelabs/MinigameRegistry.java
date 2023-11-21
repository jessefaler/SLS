package net.slimelabs;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/* Server Management System <>
 * Author: protoxon
 * Network: SlimeLabs.net
 * stores and creates MinigameConfig objects in a HashMap, so they can be accessed as needed.
 * SLS - Slime Labs Server <>
 */
public class MinigameRegistry {

    Map<String, MinigameConfig> MinigameRegistry = new HashMap<>();

    //adds a new minigame object to the registry
    public void addNewMinigame(String name, String authors, int minPlayers, int maxPlayers, int ram, boolean reset, String filePath, String MinigameDescription) {
        MinigameRegistry.put(name, new MinigameConfig(authors, minPlayers, maxPlayers, ram, reset, filePath, MinigameDescription));
    }

    public void purgeRegistry() {
        MinigameRegistry.clear();
    }

    //returns the minimum players a minigame can have
    public String getAuthors(String name) {
        return MinigameRegistry.get(name).authors;
    }

    //returns the minimum players a minigame can have
    public int getMinPlayers(String name) {
        return MinigameRegistry.get(name).minPlayers;
    }

    //returns max players a minigame can have
    public int getMaxPlayers(String name) {
        return MinigameRegistry.get(name).maxPlayers;
    }

    //returns custom ram
    public int getCustomRam(String name) {
        return MinigameRegistry.get(name).customRamMB;
    }

    public boolean getReset(String name) {
        return MinigameRegistry.get(name).reset;
    }

    //returns the folder name where the minigame server is located
    public String getFolderName(String name) {
        return MinigameRegistry.get(name).FolderName;
    }

    //returns the description for the minigame
    public String getMinigameDescription(String name) {
        return MinigameRegistry.get(name).FolderName;
    }

    public String toString() {
        StringBuilder output = new StringBuilder("§7minigame registry: ");
        for(String name : MinigameRegistry.keySet()) {
            output.append("\n§a - name: ").append(name);
            output.append("\n§3   Authors: §c").append(MinigameRegistry.get(name).authors);
            output.append("\n§3   max-players: §6").append(MinigameRegistry.get(name).maxPlayers);
            output.append("\n§3   min-players: §6").append(MinigameRegistry.get(name).minPlayers);
            output.append("\n§3   ram-allocation: §c").append(MinigameRegistry.get(name).customRamMB).append("mb");
            output.append("\n§3   reset-world: §c").append(MinigameRegistry.get(name).reset);
            output.append("\n§3   server-folder-path: §c").append(MinigameRegistry.get(name).FolderName);
            String description = MinigameRegistry.get(name).MinigameDescription;
            if(description.length() > 33) {//shorten the description to 15 characters, so it doesn't take up the screen when viewed
                description = description.substring(0, 33).trim() + "...";
            }
            output.append("\n§3   description: §c").append(description);
        }
        return output.toString();
    }

    //returns a string with a single minigames config.
    public String viewAMinigamesConfig(String name) {
        for(String key : MinigameRegistry.keySet()) {//get the correct casing of the key
            if(key.equalsIgnoreCase(name)) {
                name = key;
            }
        }
        return "§7config for " + name + "\n§a - name: " + name +
                "\n§3   Authors: §c" + MinigameRegistry.get(name).authors +
                "\n§3   max-players: §6" + MinigameRegistry.get(name).maxPlayers +
                "\n§3   min-players: §6" + MinigameRegistry.get(name).minPlayers +
                "\n§3   ram-allocation: §c" + MinigameRegistry.get(name).customRamMB + "mb" +
                "\n§3   reset-world: §6" + MinigameRegistry.get(name).reset +
                "\n§3   server-folder-path: §c" + MinigameRegistry.get(name).FolderName +
                "\n§3   description: §c" + MinigameRegistry.get(name).MinigameDescription;
    }

    //checks if a minigames name (key) is in the Map. Also ignores the casing
    public boolean containsMinigame(String name) {
        for(String key : MinigameRegistry.keySet()) {
            if(key.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    //returns the names of all minigames as a list, spaces are replaced with underscores
    //used for the OnTabComplete in the commands class
    public Iterable<String> getKeys() {
        ArrayList<String> listOfKeys = new ArrayList<>();
        for(String key : MinigameRegistry.keySet()) {
            listOfKeys.add(key.trim().replace(" ", "_").toLowerCase());
        }
        return listOfKeys;
    }

    /*
     * creates MinigameConfigObjects these store the configuration of minigames such as:
     *  The max players it can have, the minimum players it must have to start/stay playing, how much ram the server the game runs on should have
     *  the name of the server folder where the game is located
     * A record in java is a class that is meant to only store data it automatically provides the getter and setter methods.
     */
    private record MinigameConfig(String authors, int minPlayers, int maxPlayers, int customRamMB, boolean reset, String FolderName, String MinigameDescription) {}
}

