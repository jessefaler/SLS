package net.slimelabs;

import java.io.*;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/* Server Management System <>
 * Author: protoxon
 * Network: SlimeLabs.net
 * Reads in mini-game configurations from save file using YAML formatting.
 * SLS - Slime Labs Server <>
 */
public class FileHandler {
    String PATH_TO_MINIGAMES_CONFIG_FILE = "./plugins/sls/minigames.yml";
    String PATH_TO_MINIGAMES_FOLDER = "./plugins/sls/minigames";

    String PATH_TO_SLS_FOLDER = "./plugins/sls";
    public FileHandler() {
        //create Files/Folders if they don't exist
        createSLSPluginFolderIfNotExists();
        createMinigamesConfigFileIfNotExists();
        createMinigamesFolderIfNotExists();

        //read the minigame config file;
        addMinigamesToRegistryFromConfigFile();
    }

    //reads the config file, deletes the current minigame registry and replaces it with the new one we just read in.
    public void reloadMinigamesConfig() {
        SLS.MINIGAME_REGISTRY.purgeRegistry();
        addMinigamesToRegistryFromConfigFile();
    }


    //Uses A Map generated by the readYamlFile() to add minigames to the minigame registry
    @SuppressWarnings("unchecked")
    public void addMinigamesToRegistryFromConfigFile() {
        Map<String, Object> data = readYamlFile();
        assert data != null;
        List<Map<String, Object>> minigamesList = (List<Map<String, Object>>) data.get("minigames");
        for (Map<String, Object> minigame : minigamesList) {
            String name = (String) minigame.get("name");
            String authors = (String) minigame.get("authors");
            int maxPlayers = (int) minigame.get("max-players");
            int minPlayers = (int) minigame.get("min-players");
            String ram = (String) minigame.get("ram-allocation");
            boolean reset = (boolean) minigame.get("reset-world");
            String path = (String) minigame.get("server-folder-path");
            String description = (String) minigame.get("description");
            int ramInMB = convertToMegabytes(ram);
            if(ramInMB == -1) {
                SLS.PROXY.getLogger().warning("[SLS] YAML Read Error: Incorrect RAM format in minigames config file. At minigame \""
                        + name + "\" Received: \"" + ram + "\" Example Formatting: \"2gb\", \"1000mb\", \"5000kb\"");
                continue;
            }
            SLS.MINIGAME_REGISTRY.addNewMinigame(name, authors, minPlayers, maxPlayers, ramInMB, reset, path, description);
        }
    }

    //reads the Minigames Config File. Uses the snake YAML Library to read the YAML file into a Map.
    private Map<String, Object> readYamlFile() {
        try (InputStream input = new FileInputStream(PATH_TO_MINIGAMES_CONFIG_FILE)) {
            Yaml yaml = new Yaml();
            return yaml.load(input);
        } catch (Exception e) {
            SLS.PROXY.getLogger().warning("[SLS] YAML Read Error: " + e.getMessage());
            return null;
        }
    }

    //converts a ram amount to an integer representing mb
    public static int convertToMegabytes(String input) {
        // Convert the input string to lowercase for case-insensitive matching
        String lowercaseInput = input.toLowerCase();

        // Check if the input matches the expected format
        if (!lowercaseInput.matches("\\d+[gmk]b")) {
            return -1;
        }

        // Extract the numeric part of the input
        int numericValue = Integer.parseInt(lowercaseInput.replaceAll("[^0-9]", ""));

        // Determine the unit (GB, MB, KB) and convert to MB
        if (lowercaseInput.contains("gb")) {
            numericValue *= 1024; // 1 GB = 1024 MB
        } else if (lowercaseInput.contains("kb")) {
            numericValue /= 1024; // 1 KB = 1/1024 MB
        }

        return numericValue;
    }

    /* --------------The Below Methods Handle File/Folder Creation If The File Or Folder Do Not Exist--------------
     *
     * File To Create: minigames.yml (minigames config file)
     * Folder To Create: minigames (holders all the minigame server files)
     * Folder To Create: SLS (Main plugin Folder holds the Minigames Folder and the Minigames.yml file)
     *
     */


    // creates the main folder for the sls plugin
    // the sls folder holds the minigames folder and the config file
    // located in ./plugins/sls
    public void createSLSPluginFolderIfNotExists() {
        File file = new File(PATH_TO_SLS_FOLDER);
        if (!file.exists()) {
            boolean success = file.mkdir();
            if(!success) {
                SLS.PROXY.getLogger().warning("§c[SLS] ERROR: Failed To Create SLS Folder");//Send Message To Console If Failed To create New File
            }
        }
    }

    // creates the minigames folder if it doesn't already exist
    // the minigames folder holds all minigame server folders
    // located in ./plugins/sls/minigames
    public void createMinigamesFolderIfNotExists() {
        File file = new File(PATH_TO_MINIGAMES_FOLDER);
        if (!file.exists()) {
            boolean success = file.mkdir();
            if(!success) {
                SLS.PROXY.getLogger().warning("§c[SLS] ERROR: Failed To Create Minigames Folder");//Send Message To Console If Failed To create New File
            }
        }
    }

    // creates the minigames configuration file if it doesn't already exist
    // the minigames config file holds all the configuration options for each minigame
    // located in ./plugins/sls/minigames.yml
    public void createMinigamesConfigFileIfNotExists() {
        File file = new File(PATH_TO_MINIGAMES_CONFIG_FILE);
        if (!file.exists()) {
            try {
                // Create the minigames config file and writes some example configuration
                FileWriter writer = new FileWriter(file);
                String output = "#Note if reset-world is true, you will need to place a copy of the world folder in a"
                        + " folder called reset-world in the minigames server directory.\n#Make the world"
                        + " folders name matches the level-name in the server.properties file. Default level-name is \"world\"\n"
                        + "#reset-world: true is recommend for minigames\n\n"
                        + "minigames:\n  - name: 'Makers Wars' #name of the game\n"
                        + "    authors: 'MineMakers Team' #the player(s) or team that made the game\n"
                        + "    max-players: 12 #the maximum number of players a game can have\n"
                        + "    min-players: 1 #the minimum number of players a game can have\n"
                        + "    ram-allocation: '1gb' #how much ram to allocate. can use gb, mb, kb\n"
                        + "    reset-world: true #weather to reset the server world on start (suggested).\n"
                        + "    server-folder-path: './sls/servers/makers_wars/' #path to the server folder for this game\n"
                        + "    description: 'players fight to the death in a skywars based minigame' #short description of the game\n";
                writer.write(output);
                writer.close();
            } catch (IOException e) {
                SLS.PROXY.getLogger().warning("[SLS] " + e.getMessage());
            }
        }
    }
}
