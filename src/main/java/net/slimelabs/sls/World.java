package net.slimelabs.sls;

import net.slimelabs.sls.utils.MemoryConverter;

import java.nio.file.Path;


public class World {

    public Path worldPath;           // Path to the world folder
    public Path serverPath;          // Path to the server folder
    public String name;              // Name of the world
    public String[] authors;         // List of world authors
    public int maxPlayers;           // Maximum number of players allowed
    public boolean saveWorld;        // Flag to save the world
    public int ramAllocation;        // Amount of RAM allocated to the world
    public String description;       // Description of the world
    public int viewDistance;         // View distance in the world
    public String JDK;         // View distance in the world

    public World(
            Path worldPath,           // Path to the world folder
            Path serverPath,          // Path to the server folder
            String name,              // Name of the world
            String authors,         // List of world authors
            int maxPlayers,           // Maximum number of players allowed
            boolean saveWorld,        // Flag to save the world
            String ramAllocation,        // Amount of RAM allocated to the world
            String description,       // Description of the world
            int viewDistance          // View distance in the world
    ) {
        this.JDK = "java";
        this.worldPath = worldPath;
        this.serverPath = serverPath;
        this.name = name;
        this.authors = (authors != null) ? authors.split(",") : null;
        this.viewDistance = viewDistance;
        this.ramAllocation = MemoryConverter.convertToMegabytes(ramAllocation);
        this.description = (description != null) ? description : "N/A";
        this.saveWorld = saveWorld;
        this.maxPlayers = maxPlayers;
    }
}
