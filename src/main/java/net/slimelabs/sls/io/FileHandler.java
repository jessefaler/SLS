package net.slimelabs.sls.io;

import net.slimelabs.sls.SLS;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static net.slimelabs.sls.utils.Color.*;

/**
 * Handles initial file/folder creation
 */
public class FileHandler {
    String PATH_TO_SLS_FOLDER = "./plugins/sls";
    String PATH_TO_SERVERS_FOLDER = "./plugins/sls/servers";
    String PATH_TO_REGISTRY_CONFIGS_FOLDER = "./plugins/sls/registry_configs";
    String PATH_TO_REGISTRIES_FOLDER = "./plugins/sls/registries";
    String PATH_TO_TEMPLATE_CONFIG_FILE = "./plugins/sls/registry_configs/template.yml";
    public FileHandler() {
        initSLSFolder();                  // Initialize SLS folder
        initServersFolder();             // Initialize servers folder
        initRegistriesFolder();         // Initialize registries folder
        initRegistryConfigsFolder();  // Initialize registry configs folder
    }



    // --------------The Below Methods Handle File/Folder Creation If The File Or Folder Do Not Exist--------------

    // creates the main folder for the sls plugin
    // located in ./plugins/sls
    public void initSLSFolder() { // SLS
        if (new File(PATH_TO_SLS_FOLDER).mkdirs())
            SLS.LOGGER.info(DARK_GRAY + "[" + GREEN + "SLS" + DARK_GRAY + "]" + RESET + " Initialized sls folder.");
    }
    public void initServersFolder() { // Servers
        if (new File(PATH_TO_SERVERS_FOLDER).mkdirs())
            SLS.LOGGER.info(DARK_GRAY + "[" + GREEN + "SLS" + DARK_GRAY + "]" + RESET + " Initialized servers folder.");
    }
    public void initRegistryConfigsFolder() { // Registry Configs
        if (new File(PATH_TO_REGISTRY_CONFIGS_FOLDER).mkdirs()) {
            SLS.LOGGER.info(DARK_GRAY + "[" + GREEN + "SLS" + DARK_GRAY + "]" + RESET + " Initialized registry configs folder.");
            initTemplateRegistryConfig(); //Create the template registry config along with the folder
        }
    }
    public void initRegistriesFolder() { // Registries
        if (new File(PATH_TO_REGISTRIES_FOLDER).mkdirs())
            SLS.LOGGER.info(DARK_GRAY + "[" + GREEN + "SLS" + DARK_GRAY + "]" + RESET + " Initialized registries folder.");
    }

    // creates the template configuration file if it doesn't already exist
    // located in ./plugins/sls/template.yml
    public void initTemplateRegistryConfig() {
        File file = new File(PATH_TO_TEMPLATE_CONFIG_FILE);
        if (!file.exists()) {
            try (InputStream source = getClass().getClassLoader().getResourceAsStream("template.yml")) {
                if (source == null) {
                    throw new IOException("Resource template.yml not found");
                }
                Path destination = Paths.get(PATH_TO_TEMPLATE_CONFIG_FILE);

                Files.copy(source, destination);
                SLS.LOGGER.info(DARK_GRAY + "[" + GREEN + "SLS" + DARK_GRAY + "]" + RESET + " Initialized template registry config.");
            } catch (IOException e) {
                SLS.LOGGER.error("[SLS] File Copy Error: " + e);
            }
        }
    }
}
