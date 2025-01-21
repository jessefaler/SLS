package net.slimelabs.sls.namespaces;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.units.qual.A;

import java.util.*;

/**
 * Represents a single namespace
 * Holds Name, Owner, Managers and visibility
 */
public class Namespace {
    public String name; // The name of the namespace
    public UUID owner; //The UUID of the owner
    public String ownerName;
    public boolean isPrivate; // True if the namespace is private
    public Map<String, UUID> managers = new HashMap<>();
    public Namespace(String name) {
        this.name = name;
    }
    public Namespace(String name, UUID owner, String playerName) {
        this.owner = owner;
        this.name = name;
        this.ownerName = playerName;
        managers.put(playerName, owner);
    }

    public boolean hasManager(UUID uuid) {
       return managers.containsValue(uuid);
    }
    public boolean hasManager(String name) {
        return managers.containsKey(name);
    }
    public void addManager(String name, UUID uuid) {
        managers.put(name, uuid);
    }
    public void removeManager(UUID uuid) {
        // Remove the entry if the value matches the UUID
        managers.entrySet().removeIf(entry -> entry.getValue().equals(uuid));
    }

    public String toString() {
        return name;
    }

    /**
     * Gets all the managers of a namespace
     * Owners are included
     * @return a Component with a formatted string
     */
    public Component getManagersAsString() {
        TextComponent.Builder stringBuilder = Component.text(); // Use Adventure's Component builder
        // Append the header with color
        stringBuilder.append(Component.text("Managers: ").color(NamedTextColor.DARK_AQUA));
        for(String name : managers.keySet()) {
            stringBuilder.append(Component.text("\n  - ").color(NamedTextColor.GOLD))
                    .append(Component.text(name).color(NamedTextColor.GOLD)); // Apply color to the namespace name
        }
        return stringBuilder.build(); // Build the final Component
    }
}
