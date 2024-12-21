package net.slimelabs.sls.io;
import net.slimelabs.sls.SLS;

import net.slimelabs.sls.server.ServerConfiguration;
import net.slimelabs.sls.registries.Registry;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Handles parsing the region configuration files
 */
public class RegistryIO {
    public String REGISTRY_CONFIGS_FOLDER = "./plugins/sls/registry_configs";
    public String SERVERS_FOLDER = "./plugins/sls/servers";

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
    public List<Path> locateWorldFolders(Path registryDirectory) throws IOException {
        List<Path> subFolders = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(registryDirectory)) {
            paths.filter(Files::isDirectory) // Filter to get only directories
                    .filter(path -> !path.equals(registryDirectory)) // Exclude root directory
                    .forEach(subFolders::add); // Add each directory path to the list
        }
        return subFolders;
    }

    /**
     * Searches for worlds in registry folders who are not listed in the registries config file.
     * @return A hashmap with keys as the registry name and values as the Paths to the unassigned worlds.
     */
    public HashMap<String, ArrayList<Path>> getUnassignedWorlds() {
        HashMap<String, ArrayList<Path>> unassignedWorlds = new HashMap<>();
        for (Registry registry : SLS.REGISTRY_MANAGER.REGISTRIES.values()) {
            try {
                List<Path> worldFolders = locateWorldFolders(registry.path);
                Set<Path> registeredWorldPaths = registry.getWorlds().values().stream()
                        .map(world -> Path.of(world.folderName))
                        .collect(Collectors.toSet());
                List<Path> unregisteredWorldFolders = worldFolders.stream()
                        .filter(path -> !registeredWorldPaths.contains(path))
                        .toList();
                if (!unregisteredWorldFolders.isEmpty()) {
                    unassignedWorlds.put(registry.name, new ArrayList<>(unregisteredWorldFolders));
                }
            } catch (IOException e) {
                SLS.LOGGER.warn(e.toString());
            }
        }
        return unassignedWorlds;
    }

    /**
     * Searches for worlds in registry folders who are not listed in the
     * registries config file and prints them to console.
     */
    public void printUnassignedWorlds() {
        for (Registry registry : SLS.REGISTRY_MANAGER.REGISTRIES.values()) {
            try {
                List<Path> worldFolders = locateWorldFolders(registry.path);
                Set<Path> registeredWorldPaths = registry.getWorlds().values().stream()
                        .map(world -> Path.of(world.folderName))
                        .collect(Collectors.toSet());

                // Filter out unregistered world folders
                List<Path> unregisteredWorldFolders = worldFolders.stream()
                        .filter(path -> !registeredWorldPaths.contains(path))
                        .toList();

                // Ensure only root directories are kept
                List<Path> rootFolders = unregisteredWorldFolders.stream()
                        .filter(path -> unregisteredWorldFolders.stream()
                                .noneMatch(otherPath -> !path.equals(otherPath) && path.startsWith(otherPath)))
                        .toList();

                if (!rootFolders.isEmpty()) {
                    SLS.LOGGER.warn("Unregistered world folders found for registry: " + registry.name);
                    for (Path unregisteredFolder : rootFolders) {
                        SLS.LOGGER.warn(" - " + unregisteredFolder);
                    }
                }
            } catch (IOException e) {
                SLS.LOGGER.warn("Config for registry " + registry.name + " exists but no registry folder was found");
            }
        }
    }

    /**
     * Reloads all the registries.
     * Scans all for all config files present in the registry
     * Configs folder and reads them into the registry class
     */
    public void reloadAllRegistries() {
        for(Path path : getRegistryConfigs()) {
            Registry registry = readRegistryConfig(path);
            SLS.REGISTRY_MANAGER.addRegistry(registry.name, registry);
        }
        SLS.LOGGER.info("Loaded in registries: " + Arrays.toString(SLS.REGISTRY_MANAGER.getRegistryNames()));
    }

    @SuppressWarnings("unchecked")
    public Registry readRegistryConfig(Path path) {
        HashMap<String, ServerConfiguration> worlds = new HashMap<>();
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
        Path path1 = Path.of(String.valueOf(registryPath));
        for (Map<String, Object> settings : world) {

            //REQUIRED ARGUMENTS
            String worldName = getRequiredValue(settings, "name", registryName);
            String folderName = getRequiredValue(settings, "folder-name", registryName);

            //OPTIONAL ARGUMENTS
            String ram = (String) settings.getOrDefault("ram-allocation", "2048M");
            int maxPlayers = (int) settings.getOrDefault("max-players", 69);
            int viewDistance = (int) settings.getOrDefault("view-distance", 15);
            String minecraftVersion = (String) settings.getOrDefault("minecraft-version", "latest");
            String allowedClientVersions = (String) settings.getOrDefault("allowed-client-versions", null);
            boolean saveWorld = (boolean) settings.getOrDefault("save-world", false);
            String serverSoftware = (String) settings.getOrDefault("server-software", "paper");

            worldName = worldName.trim().replace(' ', '_').toLowerCase();
            ServerConfiguration serverConfiguration = new ServerConfiguration();
            serverConfiguration.name = worldName;
            serverConfiguration.ram = ram;
            serverConfiguration.viewDistance = viewDistance;
            serverConfiguration.software = serverSoftware;
            serverConfiguration.folderName = folderName;
            worlds.put(worldName, serverConfiguration);}
        return new Registry(registryName, worlds, path1);
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
