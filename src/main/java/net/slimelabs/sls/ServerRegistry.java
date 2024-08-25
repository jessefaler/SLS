package net.slimelabs.sls;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* Server Management System <>
 * Author: protoxon & Yeetoxic
 * Network: SlimeLabs.net
 * Manages all running minigame servers
 * SLS - Slime Labs Network <>
 */
public class ServerRegistry {
    //holds server instances
    Map<String, ServerInstance> SERVERS = new HashMap<>();

    //starts a server on request
    public boolean startServer(String path, int memory, String worldName) {
        if(SERVERS.containsKey(worldName)) {
            //this is here to stop two of the same server from running at the same time.
            //I plan to add the ability to run multiple of the same server at once using-
            //an id system or the servers port to identify the server in the future.
            return false;//failed to start
        }
        if (!doseDirectoryExist(path)) {
            SLS.LOGGER.warn("§c[SLS] Failed to start " + toTitleCase(worldName) + ". Directory " + path + " dose not exist.");
            return false;
        }
        if(!doseDirectoryExist(path + "/server.jar")) {
            SLS.LOGGER.warn("§c[SLS] Failed to start " + toTitleCase(worldName) + ". Could not find server.jar in " + path);
            return false;
        }
        ServerInstance server = new ServerInstance();//create an instance of the serve/r instance class
        SERVERS.put(worldName, server);//add the server instance to the SERVERS map
        server.startServer(path, memory, worldName);//start a server in server instance
        return true;
    }

    public String startServer(String worldName) {
        if(SERVERS.containsKey(worldName)) {
            //this is here to stop two of the same server from running at the same time.
            //I plan to add the ability to run multiple of the same server at once using-
            //an id system or the servers port to identify the server in the future.
            return "[§aSLS§r] §cServer already running.";
        }
        if(!SLS.REGISTRY_ROUTER.containsWorld(worldName)) {
            return "[§aSLS§r] §cServer " + worldName + " dose not exist.";
        }
        ServerInstance server = new ServerInstance();//create an instance of the server instance class
        String path = SLS.REGISTRY_ROUTER.getFolderName(worldName);
        if (!doseDirectoryExist(path)) {
            SLS.SERVER_REGISTRY.shutdownServer(worldName);
            SLS.LOGGER.warn("§c[SLS] Failed to start " + toTitleCase(worldName) + " directory " + path + " dose not exist.");
            return "[§aSLS§r] §cFailed to start " + toTitleCase(worldName) + ". Directory " + path + " dose not exist.";
        }
        if(!doseDirectoryExist(path + "/server.jar")) {
            SLS.SERVER_REGISTRY.shutdownServer(worldName);
            SLS.LOGGER.warn("§c[SLS] Failed to start " + toTitleCase(worldName) + " could not find server.jar in " + path);
            return "[§aSLS§r] §cFailed to start " + toTitleCase(worldName) + ". Could not find server.jar in " + path;
        }
        server.startServer(SLS.REGISTRY_ROUTER.getFolderName(worldName), SLS.REGISTRY_ROUTER.getRAM(worldName), worldName);//start a server in server instance
        SERVERS.put(worldName, server);//add the server instance to the SERVERS map
        return null;//no issues occurred so return null
    }

    public String shutdownServer(String worldName) {
        if(SERVERS.containsKey(worldName)) {//check if the server is running
            SERVERS.get(worldName).shutDownServer();//shutdown the server
            SERVERS.remove(worldName);//remove the server from the SERVERS map
            return "§7Shutdown server " + toTitleCase(worldName) + ".";
        }
        if(SLS.REGISTRY_ROUTER.containsWorld(worldName)) {//the minigame exists but is not running
            return toTitleCase(worldName) + " §cis not running.";
        }
        return "§cNo such server " + toTitleCase(worldName) + ".";
    }

    //shuts down all servers
    public String shutdownAllServers() {
        if(SERVERS.isEmpty()) {//no servers are running
            return "§cNo servers are running.";
        }
        for(String key : SERVERS.keySet()) {//get all servers
            SERVERS.get(key).shutDownServer();//shutdown the server
        }
        SERVERS = new HashMap<>();//set the servers Map to empty
        return "§7Shutdown all minigame servers.";
    }

    public boolean isServerOnline(String worldName) {
        if(SERVERS.get(worldName) != null) {
            return SERVERS.get(worldName).isOnline();
        }
        return false;
    }
    public String runACommand(String worldName, String command) {
        if(SERVERS.get(worldName) == null) {
            if(SLS.REGISTRY_ROUTER.containsWorld(worldName)) {
                return "§cFailed to run the command. §7" + toTitleCase(worldName) + " is not online.";
            }
            return "§cServer: " + toTitleCase(worldName) + " dose not exist.";
        }
        SERVERS.get(worldName).runCommand(command);
        return "§7executed command \"" + command + "\" on " + toTitleCase(worldName);
    }

    public boolean isShutdown(String worldName) {
        if(SERVERS.get(worldName) == null) {
            return true;
        }
        return SERVERS.get(worldName).isShutdown();
    }

    //returns a list of all online minigame servers
    //@param include all. Weather to include the text "all" in the minigame list
    public List<String> getOnlineMinigameNamesAsList(boolean includeAll) {
        ArrayList<String> output = new ArrayList<>();
        if(includeAll) {
            output.add("all");
        }
        for(String name : SERVERS.keySet()) {
            output.add(name.trim().replace(" ", "_").toLowerCase());
        }
        return output;
    }

    public boolean containsServer(String name) {
        for(String key : SERVERS.keySet()) {
            if(key.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    //returns a string of a minigame and its player count
    public String info(String worldName) {
        boolean hasGame = false;
        for(String key : SERVERS.keySet()) {//check if game is running
            if(key.equalsIgnoreCase(worldName)) {
                hasGame = true;
            }
        }
        if(!hasGame) {
            if(SLS.REGISTRY_ROUTER.containsWorld(worldName)) {
                return "§c" + worldName + " is not online";
            }
            return "§cNo such server " + worldName;
        }
        if(SERVERS.get(worldName) != null) {
            return SERVERS.get(worldName).info();
        }
        return "";
    }

    //returns a String of all minigame names and player counts
    public String info() {
        if(SERVERS.size() == 0) {//no servers online
            return "[§aSLS§r] §cNo servers are currently online.";
        }
        StringBuilder info = new StringBuilder("§3Online Minigames: \n");
        for(String key : SERVERS.keySet()) {
            info.append("§7- ").append(SERVERS.get(key).info()).append("\n");
        }
        return info.toString();
    }

    //converts a string to title casing
    public static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input; // Return the input unchanged if it's null or empty
        }

        // Split the input string into words
        String[] words = input.split("\\s+");

        // Capitalize the first letter of each word
        for (int i = 0; i < words.length; i++) {
            words[i] = capitalize(words[i]);
        }

        // Join the words back together
        return String.join(" ", words);
    }

    //capitalize a word
    public static String capitalize(String word) {
        if (word == null || word.isEmpty()) {
            return word; // Return the word unchanged if it's null or empty
        }
        // Capitalize the first letter and append the rest of the word
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    public boolean doseDirectoryExist(String path) {
        File file = new File(path);
        return file.exists();
    }
}
