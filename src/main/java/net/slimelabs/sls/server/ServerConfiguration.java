package net.slimelabs.sls.server;

public class ServerConfiguration {
    public String name; // World name (Required)
    public String[] authors;
    public String version = "latest"; // Minecraft Version to use (Default: latest)
    public String ram = "2048M"; // Amount of ram to allocate (Default: 2048mb)
    public int viewDistance = 12; // Server View Distance (Default: 12)
    public String software = "sls-paper";
    public String folderName;
}