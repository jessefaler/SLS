package net.slimelabs.sls.namespaces;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Stores and handles all registered namespaces
 */
public class NamespaceRegistry {
    public Namespace defaultNameSpace;
    HashMap<String, Namespace> namespaces = new HashMap<>();

    /**
     * gets a namespace
     * @param name the name of the namespace
     * @return return the namespace or null if
     * there isn't one with the given name
     */
    public Namespace getNamespace(String name) {
        return namespaces.get(name);
    }

    //creates a namespace
    public boolean createNamespace(String name) {
        if(!namespaces.containsKey(name)) {
            namespaces.put(name, new Namespace(name));
            return true;
        }
        return false;
    }

    public boolean renameNamespace(String name, String namespace) {
        //return false if there is already a namespace with the name
        if(namespace.contains(name)) {
            return false;
        }
        Namespace oldNamespace = namespaces.get(namespace);
        oldNamespace.name = name.trim();
        namespaces.remove(namespace);
        namespaces.put(name, oldNamespace);
        return true;
    }
    public boolean createNamespace(String name, UUID owner, String playerName) {
        if(!namespaces.containsKey(name)) {
            namespaces.put(name, new Namespace(name, owner, playerName));
            return true;
        }
        return false;
    }

    //deletes a namespace
    public void deleteNamespace(String name) {
        namespaces.remove(name);
    }

    /**
     * Gets all the namespaces a player owns
     * @param owner the UUID of the player
     * @return A List of namespaces the player owns
     */
    public List<String> getOwnedNamespaces(UUID owner) {
        List<String> ownedNamespaceStrings = new ArrayList<>();
        for (Namespace namespace : namespaces.values()) {
            if (namespace.owner != null && namespace.owner.equals(owner)) {
                ownedNamespaceStrings.add(namespace.name); // or namespace.getName() or another method
            }
        }
        return ownedNamespaceStrings;
    }

    /**
     * Checks is a player owns a namespace
     */
    public boolean isOwner(Player player, String name) {
        Namespace namespace = getNamespace(name);
        if(namespace == null) {
            return false;
        }
        return namespace.owner == player.getUniqueId();
    }

    /**
     * Gets all the namespaces a player manages
     * This includes namespaces he owns and manages
     * @param manager the UUID of the player
     * @return A List of namespaces the player manages
     */
    public List<String> getManagedNamespaces(UUID manager) {
        List<String> managedNamespaceStrings = new ArrayList<>();
        for (Namespace namespace : namespaces.values()) {
            if (namespace.managers != null && namespace.managers.containsValue(manager) || namespace.owner != null && namespace.owner.equals(manager)) {
                managedNamespaceStrings.add(namespace.name); // or namespace.getName() or another method
            }
        }
        return managedNamespaceStrings;
    }

    /**
     * Gets a players owned namespaces as a formatted string component
     * @param owner the uuid of the player
     * @return a Component with a formatted string
     */
    public Component getOwnedNamespacesString(UUID owner) {
        TextComponent.Builder stringBuilder = Component.text(); // Use Adventure's Component builder
        // Append the header with color
        stringBuilder.append(Component.text("Owned Namespaces: ").color(NamedTextColor.DARK_AQUA));
        // Append each owned namespace
        for (String name : getOwnedNamespaces(owner)) {
            stringBuilder.append(Component.text("\n  - ").color(NamedTextColor.GOLD))
                    .append(Component.text(name).color(NamedTextColor.GOLD)); // Apply color to the namespace name
        }
        return stringBuilder.build(); // Build the final Component
    }

    /**
     * Gets a players managed namespaces as a formatted string component
     * This does not include the namespaces they own
     * @param uuid the UUID of the player
     * @return a Component with a formatted string
     */
    public Component getManagedNamespacesString(UUID uuid) {
        TextComponent.Builder stringBuilder = Component.text(); // Use Adventure's Component builder
        // Append the header with color
        stringBuilder.append(Component.text("Managed Namespaces: ").color(NamedTextColor.DARK_AQUA));
        for(Namespace namespace : namespaces.values()) {
            for(UUID manager : namespace.managers.values()) {
                if(manager.equals(uuid) && !uuid.equals(namespace.owner)) {
                    stringBuilder.append(Component.text("\n  - ").color(NamedTextColor.GOLD))
                            .append(Component.text(namespace.name).color(NamedTextColor.GOLD)); // Apply color to the namespace name
                }
            }
        }
        return stringBuilder.build(); // Build the final Component
    }

}
