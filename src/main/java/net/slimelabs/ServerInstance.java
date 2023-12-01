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

    private String name;

    private boolean online;
    private boolean shutdown;

    //starts up and runs a server
    //@param path the path to the server folder
    public void startServer(String path, int memory, String minigameName) {
        File serverDirectory = new File(path);
        int port = generateRandomPort();
        name = minigameName;
        SLS.PROXY.getLogger().info("Starting server " + name
                + " on port " + port + " with " + memory + "mb ram");

        ProcessBuilder processBuilder;
        if (SLS.MINIGAME_REGISTRY.getUseCustomJDK(minigameName)) {//check if this minigame uses a custom version of java
            String javaExecutablePath = SLS.MINIGAME_REGISTRY.getCustomJDKPath(minigameName);//if so get the path to the java executable
            processBuilder = new ProcessBuilder(javaExecutablePath, "-Xmx" + memory + "M", "-jar", "server.jar", "gui", "--port", Integer.toString(port));
        } else {
            processBuilder = new ProcessBuilder("java", "-Xmx" + memory + "M", "-jar", "server.jar", "gui", "--port", Integer.toString(port));
        }
        processBuilder.directory(serverDirectory);
        processBuilder.redirectErrorStream(true);

        resetWorld(path, minigameName);//resets world if enabled for this minigame

        //runs the server on an asynchronous thread.
        runServer(processBuilder, minigameName, path);

        //add server to bungee-cord
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", port);
        String formattedName = name.trim().replace(" ", "_").toLowerCase();
        ServerInfo serverInfo = SLS.PROXY.constructServerInfo(formattedName, address, formattedName, false);
        SLS.PROXY.getServers().put(formattedName, serverInfo);
    }

    //runs the server process on an asynchronous thread.
    public void runServer(ProcessBuilder processBuilder, String minigameName, String path) {
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
                SLS.PROXY.getServers().remove(name.trim().replace(" ", "_").toLowerCase());
                shutdown = true;
                SLS.SERVER_REGISTRY.SERVERS.remove(minigameName);
                if(SLS.MINIGAME_REGISTRY.getReset(minigameName)) {//delete the world folder if world-reset is enabled to save space
                    File directoryToDelete = new File(path + "/world");
                    FileUtils.forceDelete(directoryToDelete);
                }
                int exitCode = process.waitFor();
                System.out.println("Server exited with code " + exitCode);
            } catch (IOException | InterruptedException ignored) {

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
        SLS.PROXY.getServers().remove(name.trim().replace(" ", "_").toLowerCase());
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

    //returns the server name and number of players online
    public String info() {
        String formattedName = name.trim().replace(" ", "_").toLowerCase();
        int players = SLS.PROXY.getServerInfo(formattedName).getPlayers().size();
        if(players == 1) {
            return "§a" + name + ": §7" + SLS.PROXY.getServerInfo(formattedName).getPlayers().size() + " player";
        }
        return "§a" + name + ": §7" + SLS.PROXY.getServerInfo(formattedName).getPlayers().size() + " players";
    }

    public void resetWorld(String pathToServerFolder, String minigame) {
        if(!SLS.MINIGAME_REGISTRY.getReset(minigame)) {//world reset is set to false so do nothing
            return;
        }
        File sourceDirectory = new File(pathToServerFolder + "/reset-world");
        File directoryToDelete = new File(pathToServerFolder + "/world");
        File destinationDirectory = new File(pathToServerFolder);
        try {
            if(directoryToDelete.exists()) {
                FileUtils.forceDelete(directoryToDelete);//FileUtils.copyDirectory() will replace the directory, but I had some problems with it not deleting it so i added this force delete.
            }
            FileUtils.copyDirectory(sourceDirectory, destinationDirectory);
        } catch (java.io.IOException e) {
            e.printStackTrace();
            SLS.PROXY.getLogger().warning(e.getMessage());
        }
    }

    public void runCommand(String command) {
        if(command != null && process.isAlive()) {
            try {
                OutputStream os = process.getOutputStream();
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os));
                // Example: sending a command to the server
                bw.write(command);
                bw.newLine();
                bw.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
