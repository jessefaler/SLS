package net.slimelabs.sls.server;

import net.slimelabs.sls.server.core.Flags;

public class ServerConfiguration {
    public String registry;
    public String name; // World name (Required)
    public String[] authors;
    public String version = "latest"; // Minecraft Version to use (Default: latest)
    public String ram = "2048M"; // Amount of ram to allocate (Default: 2048mb)
    public int viewDistance = 12; // Server View Distance (Default: 12)
    public String software = "paper";
    public String worldFolder; // The path to the world folder
    // Path to the folder containing all servers. This is the parent directory where server software folders reside.
    // The program will look for a subfolder named after the server software and then look for a subfolder in that.
    // Named after the version and create it if it doesn't exist.
    public String worldFolderName = "";
    Flags flags;
    public String serversFolder = "/home/jesse/Desktop/network/proxy/plugins/sls/servers";
}