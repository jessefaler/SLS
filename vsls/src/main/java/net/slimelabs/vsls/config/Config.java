package net.slimelabs.vsls.config;

import net.slimelabs.vsls.SLS;
import net.slimelabs.vsls.log.Log;
import org.yaml.snakeyaml.Yaml;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.yaml.snakeyaml.constructor.Constructor;

public class Config {

    private static final String path = "./plugins/sls/config.yml";

    // ==================================
    // Api Configuration
    // ==================================
    public ApiConfiguration api;
    public static class ApiConfiguration {
        public String url;
        public String key;
    }

    // ==================================
    // Queue Configuration
    // ==================================
    public QueueConfiguration queue = new QueueConfiguration();
    public static class QueueConfiguration {
        public int timeout = 120;
    }

    // ==================================
    // Server Lifecycle Configuration
    // ==================================
    public LifeCycleConfig lifecycle = new LifeCycleConfig();
    public static class LifeCycleConfig {
        public boolean enabled = true;
        public int check_interval = 5; // Minutes
        public int stop_delay = 20; // Seconds
    }

    /**
     * Reads the configuration from disk and returns a Config object.
     * If the config doesn't exist it will create a default config
     */
    public static Config initConfig() {
        Path targetPath = Path.of(path);
        createDefault(targetPath);

        Yaml yaml = new Yaml(new Constructor(Config.class));
        try (InputStream in = Files.newInputStream(targetPath)) {
            return yaml.load(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    /**
     * Copies a default config from resources to the target path if it doesn't exist.
     *
     * @param path Path on disk to copy to (e.g., "./plugins/sls/config.yml")
     */
    private static void createDefault(Path path) {
        if (Files.exists(path)) return;
        try {
            Files.createDirectories(path.getParent());
            try (InputStream in = Config.class.getResourceAsStream("/config.yml")) {
                if (in == null) throw new RuntimeException("Resource not found: " + "/config.yml");
                Files.copy(in, path);
            }
        } catch (IOException e) {
            Log.warn("Failed to copy default config {}", e.getMessage());
        }
    }

    /**
     * Reloads the config.yml file from disk.
     * This will replace the global singleton in the SLS class with the new config
     */
    public void reload() {
        Path targetPath = Path.of(path);

        if (!Files.exists(targetPath)) {
            createDefault(targetPath);
        }

        Yaml yaml = new Yaml(new Constructor(Config.class));
        try (InputStream in = Files.newInputStream(targetPath)) {
            SLS.config = yaml.load(in);
            Log.info("Configuration reloaded.");
        } catch (Exception e) {
            Log.error("Failed to reload configuration: {}", e.getMessage());
        }
    }

}


