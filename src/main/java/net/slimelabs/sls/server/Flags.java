package net.slimelabs.sls.server;

/**
 * Defines server flags for configuring server behavior and limits.
 */
public class Flags {
    public boolean SAVE;      // Indicates whether the server should be saved.
    public int VIEW_DISTANCE; // The number of chunks to load around a player.
    public String RAM;           // The maximum amount of RAM allocated for the server (in MB/GB).
    public int PLAYERS;       // The maximum number of players allowed on the server.

    /**
     * gets all flags as a comma-separated string
     */
    public String getFlagsAsString() {
        return "save=" + SAVE
                + ",view-distance=" + VIEW_DISTANCE
                + ",ram=" + RAM
                + ",players=" + PLAYERS;
    }

    /**
     * Merges flags <p>
     * flags1 overwrites flags2 if there are duplicate flags set
     * @param flags1 the first set of flags
     * @param flags2 the second set of flags
     * @return the merged flags
     */
    public Flags mergeFlags(Flags flags1, Flags flags2) {
        flags2.SAVE = flags1.SAVE;
        if(flags1.RAM != null) {
            flags2.RAM = flags1.RAM;
        }
        if(flags1.PLAYERS != 0) {
            flags2.PLAYERS = flags1.PLAYERS;
        }
        if(flags1.VIEW_DISTANCE != 0) {
            flags2.VIEW_DISTANCE = flags1.VIEW_DISTANCE;
        }
        return flags2;
    }

    /**
     * Accepts a comma-separated string of flags and converts them to flags.
     * Ex. "save=true,view-distance=20,players=12"
     * @param flags the comma-separated string of flags
     */
    public void parseFlagsFromString(String flags) {
        if(flags == null) {
            return;
        }
        String[] flagArray = flags.split(","); // Split the string into individual flag assignments
        for (String flag : flagArray) {
            String[] keyValue = flag.split("="); // Split each flag by the '=' symbol
            if (keyValue.length == 2) { // Ensure both key and value are present
                String key = keyValue[0].trim();
                String value = keyValue[1].trim();

                // Update the corresponding flag based on the key
                switch (key) {
                    case "save":
                        SAVE = Boolean.parseBoolean(value);
                        break;
                    case "view-distance":
                        VIEW_DISTANCE = Integer.parseInt(value);
                        break;
                    case "ram":
                        RAM = value;
                        break;
                    case "players":
                        PLAYERS = Integer.parseInt(value);
                        break;
                    default:
                        // If an unknown key is found, you can handle it (e.g., log a warning)
                        System.out.println("Unknown flag: " + key);
                }
            } else {
                // If the format is incorrect (missing '=' or value), handle this case
                System.out.println("Invalid flag format: " + flag);
            }
        }
    }
}