package net.slimelabs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/* Server Management System <>
 * Author: protoxon
 * Network: SlimeLabs.net
 * Manages all running minigame servers
 * SLS - Slime Labs Server <>
 */
public class ServerRegistry {
    //holds server instances
    Map<String, ServerInstance> SERVERS = new HashMap<>();

    //starts a server on request
    public boolean startServer(String path, int memory, String minigameName) {
        if(SERVERS.containsKey(minigameName)) {
            //this is here to stop two of the same server from running at the same time.
            //I plan to add the ability to run multiple of the same server at once using-
            //an id system or the servers port to identify the server in the future.
            return false;//failed to start
        }
        ServerInstance server = new ServerInstance();//create an instance of the server instance class
        server.startServer(path, memory, minigameName);//start a server in server instance
        SERVERS.put(minigameName, server);//add the server instance to the SERVERS map
        return true;
    }

    public String startServer(String minigameName) {
        for(String key : SLS.MINIGAME_REGISTRY.MinigameRegistry.keySet()) {//get the correct casing of the key
            if(key.equalsIgnoreCase(minigameName)) {
                minigameName = key;
                break;
            }
        }
        if(SERVERS.containsKey(minigameName)) {
            //this is here to stop two of the same server from running at the same time.
            //I plan to add the ability to run multiple of the same server at once using-
            //an id system or the servers port to identify the server in the future.
            return "Server already running.";
        }
        if(!SLS.MINIGAME_REGISTRY.containsMinigame(minigameName)) {
            return "Server " + minigameName + " dose not exist.";
        }
        ServerInstance server = new ServerInstance();//create an instance of the server instance class
        server.startServer(SLS.MINIGAME_REGISTRY.getFolderName(minigameName), SLS.MINIGAME_REGISTRY.getCustomRam(minigameName), minigameName);//start a server in server instance
        SERVERS.put(minigameName, server);//add the server instance to the SERVERS map
        return "Starting server " + minigameName;
    }

    public void shutdownServer(String minigameName) {
        for(String key : SERVERS.keySet()) {//get the correct casing of the key
            if(key.equalsIgnoreCase(minigameName)) {
                minigameName = key;
                SERVERS.get(minigameName).shutDownServer();//shutdown the server
            }
        }
        SERVERS.remove(minigameName);//remove the server from the SERVERS map
    }

    //shuts down all servers
    public void shutdownAllServers() {
        for(String key : SERVERS.keySet()) {//get all servers
            SERVERS.get(key).shutDownServer();//shutdown the server
        }
        SERVERS = new HashMap<>();//set the servers Map to empty
    }

    public boolean isServerOnline(String minigameName) {
        return SERVERS.get(minigameName).isOnline();
    }

    public boolean isShutdown(String minigameName) {
        return SERVERS.get(minigameName).isShutdown();
    }

    public Iterable<String> getOnlineMinigameNamesAsList() {
        ArrayList<String> output = new ArrayList<>();
        output.add("all");
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
}
