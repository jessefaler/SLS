package net.slimelabs.sls;
import net.slimelabs.sls.namespaces.Namespace;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages all servers running under SLS
 */
public class ServerRegistry {

    //Stores all registered servers in this format
    // Namespace --> (ServerName --> ServerInstance)
    private final Map<Namespace, Map<String, ServerInstance>> SERVERS = new HashMap<>();

    /**
     * Adds a server to the registry
     * @param namespace the namespace the server is registered under
     * @param serverInstance the instance of the server
     */
    public void addServer(Namespace namespace, ServerInstance serverInstance) {
        SERVERS.computeIfAbsent(namespace, k -> new HashMap<>()).put(serverInstance.name, serverInstance);
    }

    /**
     * Adds a server to the registry
     * @param namespace the namespace the server is registered under
     * @param serverInstance the instance of the server
     */
    public void addServer(Namespace namespace, String name, ServerInstance serverInstance) {
        SERVERS.computeIfAbsent(namespace, k -> new HashMap<>()).put(name, serverInstance);
    }

    /**
     * gets a server instance if present in the registry
     * @param namespace the namespace the server is registered under
     * @param name the instance of the server
     * @return A ServerInstance
     */
    public ServerInstance getServer(Namespace namespace, String name) {
        return SERVERS.get(namespace).get(name);
    }

    /**
     * Starts and registers a server instance.
     * @param namespace the namespace to register the server under
     * @param world the world configuration for the server to use
     */
    public void startServer(Namespace namespace, World world) {
        addServer(namespace, new ServerInstance().startServer(world, generateUniqueName(namespace, world.name), namespace));
    }

    /**
     * Generates a unique server name by appending a count in parentheses.
     * @param namespace the namespace for the server registration
     * @param baseName the original server name
     * @return a unique server name with an appended count (#)
     */
    private String generateUniqueName(Namespace namespace, String baseName) {
        if (!containsServer(namespace, baseName)) return baseName;//if there is no current server with the name return the base name
        int count = 0;
        while (containsServer(namespace, count == 0 ? baseName : baseName + "(" + (count - 1) + ")")) {
            count++;
        }
        return (count == 0) ? baseName : baseName + "(" + (count - 1) + ")";
    }

    /**
     * Gets if the specified server has been shutdown
     * @param namespace the namespace for the server registration
     * @param name the server name
     * @return true if the server is shutdown or doesn't exist in the registry
     */
    public boolean isShutdown(Namespace namespace, String name) {
        return containsServer(namespace, name) && getServer(namespace, name).isShutdown();
    }

    /**
     * Gets if the specified server is online and is ready to accept players
     * @param namespace the namespace for the server registration
     * @param name the server name
     * @return true if the server is shutdown or doesn't exist in the registry
     */
    public boolean isOnline(Namespace namespace, String name) {
        return containsServer(namespace, name) && getServer(namespace, name).online;
    }
    public boolean containsServer(Namespace namespace, String name) {
        return SERVERS.get(namespace).containsKey(name);
    }

    /**
     * Runs a command on the specified server
     * @param namespace the namespace for the server registration
     * @param name the server name
     * @param command the command to run
     * @return response (the next line of console output after the command was ran)
     */
    public String runCommandOnServer(Namespace namespace, String name, String command) {
        return getServer(namespace, name).runCommand(command);
    }

    // Shutdown all servers
    public void shutdownAllServers() {
        for (Map<String, ServerInstance> namespaceMap : SERVERS.values()) {
            for (ServerInstance server : namespaceMap.values()) {
                server.shutdownServer();
            }
        }
        SERVERS.clear();
    }

    // Shutdown all servers that are registered under the specified namespace
    public void shutdownAllServersInNamespace(Namespace namespace) {
        Map<String, ServerInstance> namespaceMap = SERVERS.get(namespace);
        for (ServerInstance server : namespaceMap.values()) {
            server.shutdownServer();
        }
    }

    // Shutdown the specified server in the given namespace.
    public void shutdownServer(Namespace namespace, String name) {
        getServer(namespace, name).shutdownServer();
    }

    /**
     * Unregisters a server from the server registry.
     * Uses a garbage collector to remove empty namespaces.
     * @param namespace the namespace for the server registration
     * @param name the server name
     */
    public void unregisterServer(Namespace namespace, String name) {
        Map<String, ServerInstance> servers = SERVERS.get(namespace);
        if (servers != null) { // Ensure the namespace exists
            servers.remove(name); // Remove the server by name
            // Check if the servers map is now empty
            if (servers.isEmpty()) {
                SERVERS.remove(namespace); // Remove the namespace if it has no servers
            }
        }
    }
}
