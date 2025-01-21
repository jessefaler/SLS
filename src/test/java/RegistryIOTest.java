import net.slimelabs.sls.World;
import net.slimelabs.sls.io.RegistryIO;
import net.slimelabs.sls.registries.Registry;
import net.slimelabs.sls.utils.Color;

import java.nio.file.Path;
import java.util.Arrays;

import static net.slimelabs.sls.utils.Color.*;

/**
 * A test class for the {@link RegistryIO} class.
 * This class reads the registry configuration files from the "registry_configs" folder
 * and outputs a list of all registries found to the console.
 */
public class RegistryIOTest {

    public static RegistryIO registryIO = new RegistryIO();
    public static void main(String[] args) {
        registryIO.SERVERS_FOLDER = "C:\\Users\\jesse\\Desktop\\SLS\\servers";
        registryIO.REGISTRY_CONFIGS_FOLDER = "C:\\Users\\jesse\\Desktop\\SLS\\registry_configs";
        System.out.println(DARK_GRAY + "—————————————————————————————————————————————————————————————————————————————————————" + RESET);
        listAllConfigs();
    }

    public static void listAllConfigs() {
        for(Path path : registryIO.getRegistryConfigs()) {
            listRegistryConfigs(path);
        }
    }

    public static void listRegistryConfigs(Path path) {
        Registry registry = registryIO.readRegistryConfig(path);
        System.out.print(MAGENTA + "REGISTRY: " + RESET + registry.name + " | Path: " + path + "\n");
        System.out.println(YELLOW + "  Worlds:" + RESET);
        for (World world : registry.getWorlds().values()) {
            System.out.println(GREEN + "    Name: " + world.name + RESET);
            System.out.println(CYAN + "      Authors: " + RESET + Arrays.toString(world.authors));
            System.out.println(CYAN + "      World Folder: " + RESET + world.worldPath);
            System.out.println(CYAN + "      RAM Allocation: " + RESET + world.ramAllocation + " MB");
            System.out.println(CYAN + "      Max Players: " + RESET + world.maxPlayers);
            System.out.println(CYAN + "      Save World: " + RESET + world.saveWorld);
            System.out.println(CYAN + "      Description: " + RESET + world.description);
            System.out.println(CYAN + "      View Distance: " + RESET + world.viewDistance);
            System.out.println(CYAN + "      JDK Version: " + RESET + world.JDK);
            System.out.println(CYAN + "      Server Folder: " + RESET + world.serverPath);
        }
        System.out.println(DARK_GRAY + "—————————————————————————————————————————————————————————————————————————————————————" + RESET);
    }
}
