
## TODO

> **Note:** Please read the [CONTRIBUTING.md](#) and [Building.md](#) before making any contributions.

> **Note:** This list is not in any particular order, but some tasks may depend on others. For example, the /sls create command will require the user data database to be completed first.

> **Note:** This list is not complete, and more tasks will be added.

> **Note:** Note once an item on the list has been complete, mark it as complete and submit a pull request with the changes to the repository. 

### 1. **Implement the `info` Command**
   - Implement the `info` command, similar to the one in the old SLS plugin.
   - Retrieve and display the following server information:
     - Server name
     - Player count
     - Server status

### 2. **Implement the `config` Command**
   - Implement the `config` command, mimicking the functionality of the old SLS plugin command.
   - Allow administrators to:
     - View registry config data.
     - See individual world configuration data.

### 3. **Display Resource Usage and Server Status (`monitor` Command)**
   - Implement the `monitor` command to display resource usage and server status.
   - Retrieve server resource usage (e.g., CPU, memory) and status from the `ServerWebSocket`.
   - Pass the information to the `Watcher` class for display to players.
   - Ensure the approach follows the example of console output forwarding in the `Watcher` class.

### 4. **Implement Routing Types for Registries**
   - Assign different routing types to registries.
   - Determine how players are routed when joining a world without a specific namespace.
   - **Note:** Consider the possibility of altering the routing system based on future requirements.

### 5. **Implement Server Flags for Players (e.g., View Distance)**
   - Modify the SLS server software egg to add environment variables for each server flag.
   - Pass these variables to the server configuration file at startup.

### 6. **Implement `/sls reset <server>` Command**
   - Allow players to reset worlds they own by:
     - Setting the save flag to `false`.
     - Restarting the server.
     - Restoring the save flag to `true` (only if it was true to begin with).
   - Ensure only the world owners (e.g., `makers_wars.protoxon`) can reset their worlds.

### 7. **Add SQLite Database for User Data and Server Ownership**
   - Implement an SQLite database to store:
     - User data.
     - Servers that each user owns.

### 8. **Implement `/sls create` Command to Create New Worlds**
   - Allow users to create new worlds with the following options:
     - World types: `default`, `super flat`, or `void`.
     - Command format: `/sls create <world_name> <world_type> <server_version|latest>`.
   - Decide whether custom worlds should be accessed through the `/sls join` command or a separate command.
   - Store the user's personal world server name and server identifier for tracking purposes.

### 9. **Implement Chest GUI for User-Friendly Interactions**
   - Provide a graphical interface for:
     - Joining worlds.
     - Creating custom worlds.
     - Resetting worlds.

---