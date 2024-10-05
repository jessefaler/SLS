package net.slimelabs.sls;

import com.velocitypowered.api.proxy.server.ServerInfo;
import net.slimelabs.sls.namespaces.Namespace;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;

import static net.slimelabs.sls.utils.Color.*;

/**
 * Represents a single server instance
 * Starts and manages a single server
 */
public class ServerInstance {
    private Process process;
    public String name; // The name of the world the server is using (specified in the config)
    public File serverPath;
    public boolean online; // Indicates whether the server has completed the startup process and is ready to accept players.
    private boolean shutdown; // Indicates whether the server failed to start or has shut down. Used to stop queuing players if the server didn't start.
    public int port; // (1024..65535)
    public boolean outputToConsole; // If true server output is printed to the proxy's console
    public Namespace namespace;
    long startTime;

    /**
     * Builds the server possess then calls runServer() to run the server
     * @param world a World object
     */
    public ServerInstance startServer(World world, String name, Namespace namespace) {
        serverPath = world.serverPath.toFile();
        port = generateRandomPort();
        startTime = System.nanoTime();
        SLS.LOGGER.info("Starting server " + BLUE + name + RESET + " on port " + BLUE + port + RESET + " with " + BLUE + world.ramAllocation + RESET + "mb ram");
        String JDK = world.JDK;
        JDK = (JDK == null) ? "java" : JDK;
        ProcessBuilder processBuilder = new ProcessBuilder(JDK, "-Xmx" + world.ramAllocation + "M", "-jar", "server.jar", "gui", "--port", Integer.toString(port));

        processBuilder.directory(serverPath);
        processBuilder.redirectErrorStream(true);

        //Runs the server on an asynchronous thread.
        runServer(processBuilder);

        //Register the server with velocity
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", port);
        String formattedName = name.trim().replace(" ", "_").toLowerCase();
        ServerInfo serverInfo = new ServerInfo(formattedName, address);
        SLS.PROXY.registerServer(serverInfo);
        return this;
    }

    public ServerInstance startServer(World world, Namespace namespace) {
        return startServer(world, world.name, namespace);
    }

    // Runs the server process on an asynchronous thread.
    private void runServer(ProcessBuilder processBuilder) {
        CompletableFuture.runAsync(() -> {
            try {
                process = processBuilder.start();

                // Redirect output to console
                BufferedReader outputReader  = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                String line;
                while ((line = outputReader.readLine()) != null) {
                    if (outputToConsole) System.out.println("[" + BLUE + name + RESET + "] " + line);
                    if (!online && line.contains("Done (")) {
                        online = true;
                        System.out.printf("[%s%s%s] %sServer %sstartup complete. Elapsed time: %s%.2f%s%s%n", BLUE, name,
                                RESET, YELLOW, RESET, RED, (System.nanoTime() - startTime) / 1000000000.0, RESET, "s");
                    }
                }

                // Read error stream for crashes or failures
                while ((line = errorReader.readLine()) != null) {
                    System.err.println("[" + RED + name + RESET + "] ERROR: " + line);
                }

                // Wait for the server to finish
                shutdown = true;
                SLS.SERVER_REGISTRY.unregisterServer(namespace, name);//Unregister the server in the server registry
                // Unregister the server in Velocity
                String formattedName = name.trim().replace(" ", "_").toLowerCase();
                SLS.PROXY.getServer(formattedName).ifPresent(server -> SLS.PROXY.unregisterServer(server.getServerInfo()));

                System.out.println("[" + BLUE + name + RESET + "] Server exited with code " + process.waitFor());
            } catch (Exception e) {
                System.out.println("[" + RED + name + RESET + "] " + e.getMessage());
            }
        });
    }

    public void shutdownServer() {
        process.destroy();
    }

    public boolean isShutdown() {
        return shutdown;
    }

    //runs a command on the server
    public String runCommand(String command) {
        if (command != null && process.isAlive()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {

                bw.write(command);
                bw.newLine();
                bw.flush();
                return br.readLine();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return "Unable to run the command; the server is not running";
    }

    //Returns the server name and number of players online
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

    //Gets a random port in the range (1024..65535)
    public int generateRandomPort() {
        int port;
        while (true) {
            port = (int) (Math.random() * (65535 - 1024 + 1) + 1024);
            if (isPortAvailable(port)) {
                return port;
            }
        }
    }

    //checks if a given port is available
    private boolean isPortAvailable(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
