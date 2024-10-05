package net.slimelabs.sls.io;

import net.slimelabs.sls.SLS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handles initial file/folder creation
 */
public class FileHandler {

    String PATH_TO_SLS_FOLDER = "./plugins/sls";
    String PATH_TO_SERVERS_FOLDER = "./plugins/sls/servers";
    String PATH_TO_REGISTRY_CONFIGS_FOLDER = "./plugins/sls/registry_configs";
    String PATH_TO_REGISTRIES_FOLDER = "./plugins/sls/registries";
    String PATH_TO_JAVA_VERSIONS_FOLDER = "./plugins/sls/java_versions";
    String PATH_TO_TEMPLATE_CONFIG_FILE = "./plugins/sls/registry_configs/template.yml";

    public FileHandler() {
        initSLSFolder();                // Initialize SLS folder
        initServersFolder();           // Initialize servers folder
        initRegistriesFolder();       // Initialize registries folder
        initJavaVersionsFolder();    // Initialize java versions folder
        initRegistryConfigsFolder();// Initialize registry configs folder
    }



    /* --------------The Below Methods Handle File/Folder Creation If The File Or Folder Do Not Exist--------------
     *
     * File To Create: template.yml (minigames config file)
     * Folder To Create: minigames (holders all the minigame server files)
     * Folder To Create: SLS (Main plugin Folder holds the Minigames Folder and the Minigames.yml file)
     *
     */


    // creates the main folder for the sls plugin
    // located in ./plugins/sls
    public void initSLSFolder() { // SLS
        if (!new File(PATH_TO_SLS_FOLDER).mkdirs())
            SLS.LOGGER.error("§c[SLS] ERROR: Failed To Create SLS Folder");
    }
    public void initServersFolder() { // Servers
        if (!new File(PATH_TO_SERVERS_FOLDER).mkdirs())
            SLS.LOGGER.error("§c[SLS] ERROR: Failed To Create Servers Folder");
    }
    public void initRegistryConfigsFolder() { // Registry Configs
        if (!new File(PATH_TO_REGISTRY_CONFIGS_FOLDER).mkdirs())
            SLS.LOGGER.error("§c[SLS] ERROR: Failed To Create Registry Configs Folder");
    }
    public void initRegistriesFolder() { // Registries
        if (!new File(PATH_TO_REGISTRIES_FOLDER).mkdirs())
            SLS.LOGGER.error("§c[SLS] ERROR: Failed To Create Registries Folder");
    }
    public void initJavaVersionsFolder() { // Java Versions
        if (!new File(PATH_TO_JAVA_VERSIONS_FOLDER).mkdirs())
            SLS.LOGGER.error("§c[SLS] ERROR: Failed To Create Java Versions Folder");
    }

    // creates the template configuration file if it doesn't already exist
    // located in ./plugins/sls/template.yml
    public void initTemplateRegistryConfigFile() {
        File file = new File(PATH_TO_TEMPLATE_CONFIG_FILE);
        if (!file.exists()) {
            try (InputStream source = getClass().getClassLoader().getResourceAsStream("template.yml")) {
                if (source == null) {
                    throw new IOException("Resource template.yml not found");
                }
                Path destination = Paths.get(PATH_TO_TEMPLATE_CONFIG_FILE, "template.yml");

                Files.copy(source, destination);
            } catch (IOException e) {
                SLS.LOGGER.error("[SLS] File Copy Error: " + e.getMessage());
            }
        }
    }
}
