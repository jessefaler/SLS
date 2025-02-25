package net.slimelabs.sls.installer;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import net.slimelabs.sls.SLS;
import net.slimelabs.sls.utils.MinecraftJavaVersionMapper;
import net.slimelabs.sls.utils.PaperJarDownloader;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class ServerInstaller {

    public static String SERVERS_FOLDER = "./plugins/sls/servers/sls-paper";

    /**
     * Installs a Minecraft server with the specified version.
     * <p>
     * This method downloads the necessary files and initializes the server.
     * <b>Note:</b> This is a blocking operation and should be executed off the main thread.
     * </p>
     *
     * @param version the Minecraft server version to download and install
     */
    public static void installServer(String version) {
        String serverDir = SERVERS_FOLDER + "/" + version;
        if(doesDirectoryExist(serverDir)) return; // Server already installed
        if(!createServerFolder(version)) { // Create the server folder
            throw new InstalliationException("Failed to create the server folder while installing " + version);
        }
        System.out.println("[SLS] Installing server: " + version);
        if(!PaperJarDownloader.downloadPaperJar(version, SERVERS_FOLDER + "/" + version)) return;
        copyResource("/server.properties", serverDir, "/server.properties");
        copyResource("/bukkit.yml", serverDir, "/bukkit.yml");
        copyResource("/spigot.yml", serverDir, "/spigot.yml");
        copyResource("/eula.txt", serverDir, "/eula.txt");
        initializeServer(serverDir, version);
    }

    public static void initializeServer(String serverDir, String version) {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String containerDirectory = "/home/container/server/merged";  // Path inside the container
        String imageName = MinecraftJavaVersionMapper.getRequiredJavaVersion(version);
        File file = new File(serverDir);
        String absolutePath = file.getAbsolutePath();

        try {
            Volume volume = new Volume(containerDirectory);
            // Create a Bind to map the host directory to the container's volume
            Bind bind = new Bind(absolutePath, volume);
            // Create a HostConfig and set the Binds
            HostConfig hostConfig = HostConfig.newHostConfig().withBinds(bind);

            // Create and start the container with the HostConfig
            CreateContainerResponse container = dockerClient.createContainerCmd(imageName)
                    .withCmd("java", "-jar", containerDirectory + "/server.jar")
                    .withHostConfig(hostConfig)
                    .exec();

            System.out.println("[SLS] Installer Container Created: " + container.getId());

            // Start the container
            dockerClient.startContainerCmd(container.getId()).exec();
            System.out.println("[SLS] Installer Container Started!");

            final boolean[] success = new boolean[1];

            // Create a log callback that we can close explicitly
            ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    String output = new String(frame.getPayload());
                    if (output.contains("Starting Minecraft server on")) {
                        success[0] = true;
                        System.out.println("Server " + version + " installation complete.");
                        try {
                            // Close the callback to signal completion
                            this.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        // Optionally remove the container once installation is complete
                        dockerClient.removeContainerCmd(container.getId())
                                .withForce(true)
                                .exec();
                    }
                }

                @Override
                public void onComplete() {
                    if (!success[0]) { // If installation did not complete successfully
                        deleteDirectory(serverDir);
                        System.err.println("Installer detected an installation error. Canceling installation");
                        if (container.getId() != null) {
                            dockerClient.removeContainerCmd(container.getId())
                                    .withForce(true)
                                    .exec();
                        }
                        throw new InstalliationException("Failed to install server " + version + ". Canceling installation");
                    }
                }
            };

            // Stream logs and wait for the callback to complete
            dockerClient.logContainerCmd(container.getId())
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .exec(callback)
                    .awaitCompletion();  // This will now return once callback.close() is called
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteDirectory(String dirPath) {
        //File file = new File(dirPath + "/session.lock");
        //file.setReadable(true, false);
        //file.setWritable(true, false);
        //unlockFile(dirPath + "/session.lock");
        Path path = Paths.get(dirPath);
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @NotNull
                @Override
                public FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    Files.delete(file); // Delete the file
                    return FileVisitResult.CONTINUE;
                }

                @NotNull
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir); // Delete the directory after its contents are deleted
                    return FileVisitResult.CONTINUE;
                }
            });
            System.out.println("Directory and its contents deleted successfully.");
        } catch (IOException e) {
            System.err.println("Error deleting directory: " + e.getMessage());
        }
    }

    public static boolean doesDirectoryExist(String path) {
        Path directoryPath = Paths.get(path);
        return Files.exists(directoryPath) && Files.isDirectory(directoryPath);
    }

    public static boolean createServerFolder(String version) {
        Path folderPath = Paths.get(SERVERS_FOLDER + "/" + version); // Change to your desired path
        try {
            Files.createDirectories(folderPath); // Creates parent directories if they don't exist
            return true;
        } catch (Exception e) {
            SLS.LOGGER.error("Server installation failed. Failed to create the server folder.");
            return false;
        }
    }

    /**
     * Copies a resource file from the program's resources to a destination directory.
     *
     * @param resourceName the name of the resource file (e.g., "server.properties" if the file is in src/main/resources)
     * @param destDir      the destination directory where the file should be copied
     * @param destFileName the name of the destination file (e.g., "server.properties" or "server.jar")
     */
    public static void copyResource(String resourceName, String destDir, String destFileName) {
        Path targetPath = Paths.get(destDir + "/" + destFileName);
        try (InputStream resourceStream = SLS.class.getResourceAsStream(resourceName)) {
            if (resourceStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourceName);
            }
            // Copy the resource to the target location, replacing any existing file
            Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            SLS.LOGGER.error("Failed to copy resource: " + resourceName + " to " + destDir);
            e.printStackTrace();
        }
    }

    public static void unlockFile(String lockFilePath) {
        RandomAccessFile file = null;
        FileChannel fileChannel = null;
        FileLock fileLock = null;

        try {
            // Open the lock file for reading and writing
            file = new RandomAccessFile(lockFilePath, "rw");
            fileChannel = file.getChannel();

            // Try to acquire the lock (if it's already locked by someone else, it will throw an exception)
            fileLock = fileChannel.lock();

            // If the lock was successfully acquired, release it
            if (fileLock != null) {
                fileLock.release();
                System.out.println("Lock on file '" + lockFilePath + "' released successfully.");
            }

        } catch (IOException e) {
            System.err.println("Failed to unlock the file: " + e.getMessage());
        } finally {
            try {
                // Ensure resources are closed properly
                if (fileLock != null && fileLock.isValid()) {
                    fileLock.release();
                }
                if (fileChannel != null) {
                    fileChannel.close();
                }
                if (file != null) {
                    file.close();
                }
            } catch (IOException e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
    }
}
