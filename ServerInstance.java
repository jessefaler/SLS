package net.slimelabs;


import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/* Server Management System <>
 * Author: protoxon
 * Network: SlimeLabs.net
 * starts up and runs a server
 * SLS - Slime Labs Server <>
 */
public class ServerInstance {

    private Process process;

    private int memory;
    private String name;

    private boolean online;

    private boolean shutdown;

    //starts up and runs a server
    //@param path the path to the server folder
    public void startServer(String path, int memory, String minigameName) {
        File serverDirectory = new File(path);
        int port = generateRandomPort();
        this.memory = memory;
        name = minigameName;
        SLS.PROXY.getLogger().info("Starting server " + name
                + " on port " + port + " with " + memory + "mb ram");
        ProcessBuilder processBuilder = new ProcessBuilder("java", "-Xmx" + memory + "M", "-jar", "server.jar", "gui", "--port", Integer.toString(port));
        processBuilder.directory(serverDirectory);
        processBuilder.redirectErrorStream(true);

        resetWorld(path, minigameName);//resets world if enabled for this minigame

        //runs the server on an asynchronous thread.
        runServer(processBuilder, minigameName);

        //add server to bungee-cord
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", port);
        name = name.trim().replace(" ", "_").toLowerCase();
        ServerInfo serverInfo = SLS.PROXY.constructServerInfo(name, address, name, false);
        SLS.PROXY.getServers().put(name, serverInfo);
    }

    //runs the server process on an asynchronous thread.
    public void runServer(ProcessBuilder processBuilder, String minigameName) {
        CompletableFuture.runAsync(() -> {
            try {
                process = processBuilder.start();
                // Redirect output to console
                InputStream is = process.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println("[" + name + "] " + line);
                    if(online || line.contains("Done (")) {
                        online = true;
                    }
                    else {//for debug players show console in action bar
                        TextComponent textComponent = null;
                        textComponent = new TextComponent("§7" + line.replaceAll("\\[.*?\\]", "").replace(":", "").trim());
                        for(UUID uuid : SLS.PLAYER_CONNECTOR.playersInQueue.keySet()) {
                            if(SLS.PLAYER_CONNECTOR.debugEnabledPlayers.contains(uuid) && SLS.PLAYER_CONNECTOR.playersInQueue.get(uuid).equals(minigameName)) {
                                ProxyServer.getInstance().getPlayer(uuid).sendMessage(ChatMessageType.ACTION_BAR, textComponent);
                            }
                        }
                    }
                }

                // Wait for the server to finish
                SLS.PROXY.getServers().remove(name);
                shutdown = true;
                int exitCode = process.waitFor();
                System.out.println("Server exited with code " + exitCode);
            }
            catch (IOException | InterruptedException e) {
                e.printStackTrace();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public int generateRandomPort() {
        int port;
        while (true) {
            port = (int) (Math.random() * (65535 - 1024 + 1) + 1024);
            if (isPortAvailable(port)) {
                return port;
            }
        }
    }

    //check if port is available -------------------------
    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void shutDownServer() {
        process.destroy();
        SLS.PROXY.getServers().remove(name);
    }

    public boolean isOnline() {
        return online;
    }

    public boolean isShutdown() {
        return shutdown;
    }
    public String getName() {
        return name;
    }

    public String info() {
        ServerInfo serverInfo = SLS.PROXY.getServerInfo(name);
        String serverName = serverInfo.getName();
        int playersOnline = serverInfo.getPlayers().size();
        String info = "";
        info += ChatColor.WHITE + "Server Name: " + ChatColor.AQUA + serverName + "\n";
        info += ChatColor.WHITE + "  Online Players: " + ChatColor.RED + playersOnline + "\n";
        info += ChatColor.WHITE + "  Memory: " + ChatColor.RED + memory + "mb\n";
        return info;
    }

    public void resetWorld(String pathToServerFolder, String minigame) {
        if(!SLS.MINIGAME_REGISTRY.getReset(minigame)) {//world reset is set to false so do nothing
            return;
        }
        File sourceDirectory = new File(pathToServerFolder + "/reset-world");
        File destinationDirectory = new File(pathToServerFolder);
        try {
            FileUtils.copyDirectory(sourceDirectory, destinationDirectory);
        } catch (java.io.IOException e) {
            SLS.PROXY.getLogger().warning(e.getMessage());
        }
    }
}
