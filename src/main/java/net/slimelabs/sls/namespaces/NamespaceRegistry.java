package net.slimelabs.sls.namespaces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

/**
 * Stores and handles all registered namespaces
 */
public class NamespaceRegistry {
    public Namespace defaultNameSpace;
    HashMap<String, Namespace> namespaces = new HashMap<>();

    //gets a namespace
    public Namespace getNamespace(String name) {
        return namespaces.get(name);
    }

    //creates a namespace
    public void createNamespace(String name) {
        namespaces.computeIfAbsent(name, Namespace::new);
    }

    //deletes a namespace
    public void deleteNamespace(String name) {
        namespaces.remove(name);
    }

    /**
     * Gets all the namespaces a player owns
     * @param owner the UUID of the player
     * @return An arraylist of namespaces the player owns
     */
    public ArrayList<Namespace> getOwnedNamespaces(UUID owner) {
        ArrayList<Namespace> ownedNamespaces = new ArrayList<>();
        for(Namespace namespace : namespaces.values()) {
            if(namespace.owner.equals(owner)) {
                ownedNamespaces.add(namespace);
            }
        }
        return ownedNamespaces;
    }

}
