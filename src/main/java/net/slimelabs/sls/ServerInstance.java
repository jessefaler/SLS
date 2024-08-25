package net.slimelabs.sls;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;


/* Server Management System <>
 * Author: protoxon & Yeetoxic
 * Network: SlimeLabs.net
 * starts up and runs a server
 * SLS - Slime Labs Network <>
 */
public class ServerInstance {

    private Process process;

    private String name;

    private boolean online;
    private boolean shutdown;

    //starts up and runs a server
    //@param path the path to the server folder
    public void startServer(String path, int memory, String worldName) {
        File serverDirectory = new File(path);
        int port = generateRandomPort();
        name = worldName;
        SLS.LOGGER.info("Starting server " + name
                + " on port " + port + " with " + memory + "mb ram");

        ProcessBuilder processBuilder;
        if (SLS.REGISTRY_ROUTER.getUseCustomJDK(worldName)) {//check if this minigame uses a custom version of java
            String javaExecutablePath = SLS.REGISTRY_ROUTER.getCustomJDKPath(worldName);//if so get the path to the java executable
            processBuilder = new ProcessBuilder(javaExecutablePath, "-Xmx" + memory + "M", "-jar", "server.jar", "gui", "--port", Integer.toString(port));
        } else {
            processBuilder = new ProcessBuilder("java", "-Xmx" + memory + "M", "-jar", "server.jar", "gui", "--port", Integer.toString(port));
        }
        processBuilder.directory(serverDirectory);
        processBuilder.redirectErrorStream(true);

        resetWorld(path, worldName);//resets world if enabled for this minigame

        //runs the server on an asynchronous thread.
        runServer(processBuilder, worldName, path);

        //add server to bungee-cord
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", port); // Example IP and port
        String formattedName = name.trim().replace(" ", "_").toLowerCase();
        ServerInfo serverInfo = new ServerInfo(formattedName, address);
        RegisteredServer registeredServer = SLS.PROXY.registerServer(serverInfo);

    }

    //runs the server process on an asynchronous thread.
    public void runServer(ProcessBuilder processBuilder, String worldName, String path) {
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
                        Component component = Component.text("§7" + line.replaceAll("\\[.*?\\]", "").replace(":", "").trim()).color(NamedTextColor.GRAY);

                        for (UUID uuid : SLS.PLAYER_CONNECTOR.playersInQueue.keySet()) {
                            if (SLS.PLAYER_CONNECTOR.debugEnabledPlayers.contains(uuid) && SLS.PLAYER_CONNECTOR.playersInQueue.get(uuid).equals(worldName)) {
                                SLS.PROXY.getPlayer(uuid).ifPresent(player -> player.sendActionBar(component));
                            }
                        }
                    }
                }

                // Wait for the server to finish
                String formattedName = name.trim().replace(" ", "_").toLowerCase();
                if (SLS.PROXY.getServer(formattedName).isPresent()) {
                    SLS.PROXY.unregisterServer(SLS.PROXY.getServer(formattedName).get().getServerInfo());
                }
                shutdown = true;
                SLS.SERVER_REGISTRY.SERVERS.remove(worldName);
                if(SLS.REGISTRY_ROUTER.getReset(worldName)) {//delete the world folder if world-reset is enabled to save space
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
        String formattedName = name.trim().replace(" ", "_").toLowerCase();
        if (SLS.PROXY.getServer(formattedName).isPresent()) {
            SLS.PROXY.unregisterServer(SLS.PROXY.getServer(formattedName).get().getServerInfo());
        }
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

        int players = 0;
        if (SLS.PROXY.getServer(formattedName).isPresent()) {
            players = SLS.PROXY.getServer(formattedName).get().getPlayersConnected().size();
        }
        if(players == 1) {
            return "§a" + name + ": §7" + SLS.PROXY.getServer(formattedName).get().getPlayersConnected().size() + " player";
        }
        return "§a" + name + ": §7" + SLS.PROXY.getServer(formattedName).get().getPlayersConnected().size() + " players";
    }

    public void resetWorld(String pathToServerFolder, String minigame) {
        if(!SLS.REGISTRY_ROUTER.getReset(minigame)) {//world reset is set to false so do nothing
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
            SLS.LOGGER.warn(e.getMessage());
        }
    }

    public void runCommand(String command) {
        if(command != null && process.isAlive()) {
            try {
                OutputStream os = process.getOutputStream();
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(os));
                bw.write(command);
                bw.newLine();
                bw.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
