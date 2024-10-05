package net.slimelabs.sls.namespaces;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Represents a single namespace
 * Holds Name, Owner, Managers and visibility
 */
public class Namespace {
    public String name; // The name of the namespace
    public UUID owner; //The UUID of the owner
    public boolean isPrivate; // True if the namespace is private
    public ArrayList<UUID> managers; // List of managers UUID's
    public Namespace(String name) {
        this.name = name;
    }
}
