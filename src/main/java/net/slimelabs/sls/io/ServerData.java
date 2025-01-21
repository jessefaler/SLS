package net.slimelabs.sls.io;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles interactions with the server data database, including retrieving and storing data.
 * This class manages the connection to the database, ensuring the connection persists throughout
 * the program's execution. It also takes care of the creation of the database if it does not already exist.
 * <p>
 * Responsibilities: <p>
 * - Establishing and maintaining a persistent database connection. <p>
 * - Retrieving and storing server-related data from the database. <p>
 * - Creating the database if it does not already exist. <p>
 */
public class ServerData {

    public static Connection connection;
    public static final String SERVER_DATA = "ServerData.db";
    public static final String PATH = "./plugins/sls";

                                               // JDBC PROTOCOL - DATABASE: SQLITE - PATH
    private static final String DATABASE_URL = "jdbc:sqlite:" + PATH + "/" + SERVER_DATA;

    /**
     * Connects to the database
     */
    public static void connect() {
        createDatabaseFile(); // Create the database file if it doesn't exist
        try {
            Class.forName("org.sqlite.JDBC"); // Required to fix an issue with the jdbc driver not being present
            // See (https://stackoverflow.com/questions/16725377/unable-to-connect-to-database-no-suitable-driver-found_
            connection = DriverManager.getConnection(DATABASE_URL);
            createServerDataTable();
        } catch (SQLException e) {
            System.err.println("[SLS] SQLite: Connection to (" + SERVER_DATA + ") failed. \n" + e.getMessage());
        } catch (ClassNotFoundException e) {
            System.err.println("[SLS] SQLite: Class Not Found");
        }
    }

    public static void createDatabaseFile() {
        File databaseFile = new File(PATH + "/" + SERVER_DATA);
        if (!databaseFile.exists()) {
            try {
                boolean created = databaseFile.createNewFile();
                if (created) {
                    System.out.println("[SLS] SQLite: Database file created. " + SERVER_DATA);
                }
            } catch (IOException e) {
                System.err.println("[SLS] SQLite: Error creating the database file. " + SERVER_DATA);
                System.err.println(e.getMessage());
            }
        }
    }

    /**
     * Deletes the entry in the ServerData table with the given identifier.
     *
     * @param name The name of the server data to be deleted.
     * @return {@code true} if the entry was successfully deleted; {@code false} if no entry was found or an error occurred.
     */
    public static boolean deleteServerData(String name) {
        if (!validateConnection()) {
            return false;  // Return false if the connection is invalid
        }

        String deleteSQL = "DELETE FROM ServerData WHERE name = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(deleteSQL)) {
            preparedStatement.setString(1, name);
            int rowsAffected = preparedStatement.executeUpdate();

            // If rowsAffected is 1, the deletion was successful; otherwise, no such entry was found.
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("[SLS] SQLite: An error occurred while deleting server data for server " + name + " in " + SERVER_DATA);
            System.err.println(e.getMessage());
            return false;  // Return false if an error occurs during deletion
        }
    }

    /**
     * Generates the SQL statement to create the ServerData table.
     *
     * @return A SQL string to create the ServerData table
     */
    public static String serverDataTable() {
        return """
        CREATE TABLE IF NOT EXISTS ServerData (
            name TEXT PRIMARY KEY, -- Unique identifier for the server Ex. (b4e844f4)
            saved_flags TEXT       -- A comma-separated string of key-value pairs representing flags (save=true,players=20)
        );
        """;
    }

    /**
     * Creates the ServerData table if it doesn't exist.
     */
    public static void createServerDataTable() {
        try {
            if (connection == null || connection.isClosed()) return;
            connection.createStatement().execute(serverDataTable());
        } catch (SQLException e) {
            System.err.println("[SLS] SQLite: Failed to create ServerData table in " + SERVER_DATA);
        }
    }

    /**
     * Validates the database connection by checking its current status.
     * If the connection is inactive, it will attempt to re-establish the connection once.
     *
     * @return {@code true} if the connection is valid and active; {@code false} otherwise.
     */
    public static boolean validateConnection() {
        try {
            if(connection != null && !connection.isClosed()) { // Connection is valid so return true
                return true;
            }
            connect(); // Connection is not valid, so try to connect
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            System.err.println("[SLS] SQLite: An Error Occurred while validating the connection to " + SERVER_DATA);
            System.err.println(e.getMessage());
        }
        return false;
    }

    private static String mergeFlags(String oldFlags, String newFlags) {
        Map<String, String> oldFlagsMap = parseFlags(oldFlags);
        Map<String, String> newFlagsMap = parseFlags(newFlags);

        oldFlagsMap.putAll(newFlagsMap);

        StringBuilder mergedFlags = new StringBuilder();
        for (Map.Entry<String, String> entry : oldFlagsMap.entrySet()) {
            if (!mergedFlags.isEmpty()) {
                mergedFlags.append(",");
            }
            mergedFlags.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return mergedFlags.toString();
    }

    // Helper method to parse a flag string into a map
    private static Map<String, String> parseFlags(String flags) {
        Map<String, String> flagMap = new HashMap<>();
        String[] flagArray = flags.split(",");
        for (String flag : flagArray) {
            String[] keyValue = flag.split("=");
            if (keyValue.length == 2) {
                flagMap.put(keyValue[0], keyValue[1]);
            }
        }
        return flagMap;
    }

    /**
     * Inserts flags for a server. <p>
     * If the server already exists, the new flags will be merged with the existing flags.
     * Otherwise, it creates a new entry for the server.
     *
     * @param name  the name of the server
     * @param flags flags in a comma-separated string
     */
    public static void insertServerFlags(String name, String flags) {
        if (!validateConnection()) return;

        // Check if the server already exists
        String oldFlags = getServerFlags(name);
        if (oldFlags != null) {
            // If the server exists, merge the flags
            flags = mergeFlags(oldFlags, flags); // Merge with existing flags

            // Update the existing entry
            String updateSQL = "UPDATE ServerData SET saved_flags = ? WHERE name = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(updateSQL)) {
                preparedStatement.setString(1, flags);
                preparedStatement.setString(2, name);
                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[SLS] SQLite: An Error Occurred while updating server data in " + SERVER_DATA);
                System.err.println(e.getMessage());
            }
        } else {
            // If the server does not exist, insert a new entry
            String insertSQL = "INSERT INTO ServerData(name, saved_flags) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertSQL)) {
                preparedStatement.setString(1, name);
                preparedStatement.setString(2, flags);
                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                System.err.println("[SLS] SQLite: An Error Occurred while inserting server data in " + SERVER_DATA);
                System.err.println(e.getMessage());
            }
        }
    }

    /**
     * Retrieves the flags associated with a specific server from the database.
     *
     * @param name The unique name for the server whose flags are to be fetched.
     * @return The flags as a comma-separated string, or {@code null} if no flags are found for the given identifier.
     */
    public static String getServerFlags(String name) {
        if (!validateConnection()) return null;
        // Query to get the flags for the server
        String selectSQL = "SELECT saved_flags FROM ServerData WHERE name = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(selectSQL)) {
            preparedStatement.setString(1, name);
            ResultSet resultSet = preparedStatement.executeQuery();

            // Check if the server exists and return the flags
            if (resultSet.next()) {
                return resultSet.getString("saved_flags");
            }
        } catch (SQLException e) {
            System.err.println("[SLS] SQLite: An Error Occurred while retrieving server data from " + SERVER_DATA);
            System.err.println(e.getMessage());
        }
        return null;
    }
}
