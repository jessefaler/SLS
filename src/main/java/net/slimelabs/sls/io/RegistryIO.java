package net.slimelabs.sls.io;
import net.slimelabs.sls.SLS;

import net.slimelabs.sls.World;
import net.slimelabs.sls.registries.Registry;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;


/**
 * Handles parsing the region configuration files
 */
public class RegistryIO {

    public String REGISTRY_CONFIGS_FOLDER = "./plugins/sln/registry_configs";
    public String SERVERS_FOLDER = "./plugins/sln/servers";


    /**
     * Retrieves a list of all files in the Registry Configs directory that have
     * the extension ".yml" or ".yaml".
     * @return a list of {@link Path} objects representing files
     */
    public List<Path> getRegistryConfigs() {
        List<Path> yamlFilePaths = new ArrayList<>();
        Path dirPath = Paths.get(REGISTRY_CONFIGS_FOLDER);
        try (Stream<Path> paths = Files.walk(dirPath)) {
            paths.filter(path -> path.toString().endsWith(".yaml") || path.toString().endsWith(".yml"))
                    .forEach(yamlFilePaths::add);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return yamlFilePaths;
    }
    public List<Path> locateWorldFolders(Path registryDirectory) {
        List<Path> subFolders = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(registryDirectory)) {
            paths.filter(Files::isDirectory) // Filter to get only directories
                    .filter(path -> !path.equals(registryDirectory)) // Exclude root directory
                    .forEach(subFolders::add); // Add each directory path to the list
        } catch (IOException e) {
            e.printStackTrace();
        }
        return subFolders;
    }

    public void reloadAllRegistries() {
        for(Path path : getRegistryConfigs()) {
            Registry registry = readRegistryConfig(path);
            SLS.REGISTRY_MANAGER.addRegistry(registry.name, registry);
        }
    }

    @SuppressWarnings("unchecked")
    public Registry readRegistryConfig(Path path) {
        HashMap<String, World> worlds = new HashMap<>();
        Map<String, Object> data = readYML(path);
        assert data != null;
        //read in registry settings (name, path)
        List<Map<String, Object>> registry = (List<Map<String, Object>>) data.get("registry");
        String registryName = null;
        StringBuilder registryPath = null;
        for (Map<String, Object> settings : registry) {
            registryName = getRequiredValue(settings,"name", path.toString());
            registryPath = new StringBuilder(getRequiredValue(settings, "path", registryName));
        }

        //read in world configuration data
        List<Map<String, Object>> world = (List<Map<String, Object>>) data.get("worlds");
        for (Map<String, Object> settings : world) {

            //REQUIRED ARGUMENTS
            String worldName = getRequiredValue(settings, "name", registryName);
            String folderName = getRequiredValue(settings, "folder-name", registryName);
            String serverFolderName = getRequiredValue(settings, "server-folder", registryName);

            //OPTIONAL ARGUMENTS
            String ram = (String) settings.getOrDefault("ram-allocation", "2048M");
            int maxPlayers = (int) settings.getOrDefault("max-players", 69);
            boolean saveWorld = (boolean) settings.getOrDefault("save-world", false);
            int viewDistance = (int) settings.getOrDefault("view-distance", 15);
            String authors = getOptionalValue(settings, "authors");
            String description = getOptionalValue(settings, "description");
            Path serverFolderPath = Path.of(SERVERS_FOLDER, serverFolderName);

            assert registryPath != null;
            registryPath.append(registryPath.toString().endsWith("\\") ? "" : "\\").append(folderName);
            worlds.put(worldName, new World(Path.of(String.valueOf(registryPath)), serverFolderPath, worldName, authors, maxPlayers, saveWorld, ram, description, viewDistance));
        }
        return new Registry(registryName, worlds);
    }

    //Reads a YML file into a Map
    private Map<String, Object> readYML(Path path) {
        try (InputStream input = new FileInputStream(path.toFile())) {
            Yaml yaml = new Yaml();
            return yaml.load(input);
        } catch (Exception e) {
            SLS.LOGGER.error("[SLN] YAML Read Error: " + e.getMessage());
            return null;
        }
    }

    private static String getRequiredValue(Map<String, Object> config, String key, String registryName) {
        String[] keys = key.split("\\.");
        Map<String, Object> currentMap = config;

        for (int i = 0; i < keys.length - 1; i++) {
            currentMap = (Map<String, Object>) currentMap.get(keys[i]);
            if (currentMap == null) {
                throw new IllegalArgumentException("Missing required configuration key: " + key + " in " + registryName + " registry at " + config.get("name"));
            }
        }
        Object value = currentMap.get(keys[keys.length - 1]);
        if (value == null) {
            throw new IllegalArgumentException("Missing required configuration key: " + key + " in " + registryName + " registry at " + config.get("name"));
        }
        return value.toString();
    }

    private static String getOptionalValue(Map<String, Object> config, String key) {
        String[] keys = key.split("\\.");
        Map<String, Object> currentMap = config;

        for (int i = 0; i < keys.length - 1; i++) {
            currentMap = (Map<String, Object>) currentMap.get(keys[i]);
            if (currentMap == null) {
                return null; // Return null if the path doesn't exist
            }
        }
        return (String) currentMap.get(keys[keys.length - 1]);
    }
}
