# vSLS Command Documentation

---

## Table of Contents

- [Info Command](#info-command)
- [Create Command](#create-command)
- [Start Command](#start-command)
- [Join Command](#join-command)
- [System Command](#system-command)
- [Console Command](#console-command)
- [Blueprint Command](#blueprint-command)
- [Debug Command](#debug-command)
- [Delete Command](#delete-command)
- [Logs Command](#logs-command)
- [Reload Command](#reload-command)
- [Stop Command](#stop-command)
- [Kill Command](#kill-command)
- [Dequeue Command](#dequeue-command)
- [Status Command](#status-command)
- [Stats Command](#stats-command)
- [Version Command](#version-command)

---

## Info Command

**Permission:** `sls.command.admin` (required for server-specific info)

**Description:** Displays information about active servers. When used without arguments, it shows a list of all active servers with their player counts. When used with a server ID, it displays detailed information about that specific server.

**Usage:**
```
/sls info
/sls info <server>
```

**Details:**
- `/sls info` - Lists all active servers with their current player counts and status. Server status is color-coded:
  - Green: Running
  - Yellow: Starting
  - Red: Stopping
  - Dark Red: Offline
- `/sls info <server>` - Displays detailed information about the specified server, including:
  - Player count (with hoverable list of player names)
  - Server status
  - Blueprint ID
  - Blueprint type
  - Server software and version
  - Resource statistics:
    - CPU usage
    - Memory usage (current/max with percentage)
    - Network inbound/outbound traffic
    - Server uptime

---

## Create Command

**Permission:** `sls.command.admin`

**Description:** Creates a new server from a blueprint. This command only creates the server; it does not start it. Use the `start` command to start a created server.

**Usage:**
```
/sls create <blueprint_type> <blueprint_id>
```

**Arguments:**
- `blueprint_type` - The type of blueprint (e.g., "survival", "creative", "lobby")
- `blueprint_id` - The specific blueprint ID to use for server creation

**Details:**
- The command provides tab completion for both blueprint types and IDs
- Upon successful creation, the command displays the created server's ID
- If creation fails, an error message is displayed with the reason

---

## Start Command

**Permission:** `sls.command.admin`

**Description:** Starts an existing server. This command is only effective for servers where saving is enabled. The server must already exist (created via the `create` command or previously started).

**Usage:**
```
/sls start <blueprint_type> <blueprint_id>
```

**Arguments:**
- `blueprint_type` - The type of blueprint
- `blueprint_id` - The specific blueprint ID

**Details:**
- The command will create the server if it doesn't exist, then start it
- Tab completion is available for blueprint types and IDs
- A success message is displayed when the server starts, including the server ID

---

## Join Command

**Permission:** `sls.command.admin` (required to join other players)

**Description:** Combines server creation, starting, and player connection into a single command. It creates the server if it doesn't exist, starts it, waits for the server status to change to "ready", and then connects the specified players (or the command executor) to the server.

**Usage:**
```
/sls join <blueprint_type> <blueprint_id>
/sls join <blueprint_type> <blueprint_id> [all | local | <player>]
```

**Arguments:**
- `blueprint_type` - The type of blueprint
- `blueprint_id` - The specific blueprint ID
- `[all | local | <player>]` (optional) - Specifies which players to connect:
  - `all` - Connects all players currently on the proxy
  - `local` - Connects all players on the same server as the command sender
  - `<player>` - Connects a single specific player by username

**Details:**
- If no player argument is provided, the command executor is connected to the server
- The command automatically handles server creation, starting, and waiting for readiness
- Requires admin permission to join other players; players can join themselves without permission

---

## System Command

**Permission:** `sls.command.admin`

**Description:** Displays version and system information about the SLS Protocube daemon, including hardware and OS details.

**Usage:**
```
/sls system
```

**Details:**
The command displays the following system information:
- Version
- Architecture
- CPU Threads
- Memory (formatted in appropriate units: KB, MB, GB, or TB)
- Kernel Version
- Operating System
- OS Type

---

## Console Command

**Permission:** `sls.command.admin`

**Description:** Executes a command on a specific server's console and attempts to capture and display the command output.

**Usage:**
```
/sls console <server> <command>
```

**Arguments:**
- `server` - The server ID where the command should be executed
- `command` - The command to execute (can include arguments)

**Details:**
- The command automatically strips leading slashes (`/`) if present
- The command attempts to capture output by checking server logs with increasing delays:
  - 100ms delay → reads 8 log lines
  - 800ms delay → reads 12 log lines
  - 3000ms delay → reads 25 log lines
- Output is formatted and displayed, with error messages highlighted in red
- If no output is found after all attempts, a "No output found" message is displayed
- Supports both legacy server format (`>command`) and newer format (`command`)

---

## Blueprint Command

**Permission:** `sls.command.admin`

**Description:** Displays detailed information about a blueprint in a formatted YAML-like structure with color-coded fields.

**Usage:**
```
/sls blueprint <blueprint_id>
```

**Arguments:**
- `blueprint_id` - The ID of the blueprint to display

**Details:**
- Displays the blueprint's complete configuration in a formatted, readable structure
- Fields are organized in a logical order: metadata, world, server, saving, annotations
- Top-level keys are displayed in gold, nested keys in dark grey, and values in red
- The output includes all blueprint configuration details such as:
  - Metadata (id, name, type)
  - World configuration
  - Server software and version
  - Resource limits
  - Saving settings
  - Configuration files
  - Content settings

---

## Debug Command

**Permission:** `sls.command.admin`

**Description:** Toggles debug mode for the command executor. When enabled, the player will receive additional debug information in chat and logs.

**Usage:**
```
/sls debug
```

**Details:**
- This command can only be executed by a player (not console)
- Toggles debug mode on/off for the executing player
- When enabled, displays "Debug mode enabled"
- When disabled, displays "Debug mode disabled"
- Debug mode affects what log messages and debug information the player receives

---

## Delete Command

**Permission:** `sls.command.admin`

**Description:** Deletes a server or all servers. This permanently removes the server(s) from the system.

**Usage:**
```
/sls delete <server>
/sls delete all
```

**Arguments:**
- `server` - The server ID to delete
- `all` - Deletes all servers

**Details:**
- Deleting a server permanently removes it and all associated data
- When using `all`, the command deletes all servers in the system
- Success and failure messages are displayed for each deletion attempt
- The command fetches server information from the API before deletion

---

## Logs Command

**Permission:** `sls.command.admin`

**Description:** Displays the console logs for a given server. By default, shows recent logs, or a specified number of log lines.

**Usage:**
```
/sls logs <server>
/sls logs <server> <lines>
```

**Arguments:**
- `server` - The server ID to view logs for
- `lines` (optional) - The number of log lines to display (defaults to a standard amount if not specified)

**Details:**
- Logs are displayed in a formatted output with clear start and end markers
- If a line count is specified, only that many recent log lines are shown
- Log lines are displayed in grey text for readability
- The command validates that the line count is a valid number

---

## Reload Command

**Permission:** `sls.command.admin`

**Description:** Reloads certain parts of the SLS system, including configuration, blueprints, and software configurations.

**Usage:**
```
/sls reload
/sls reload all
/sls reload config
/sls reload blueprints
/sls reload software
```

**Arguments:**
- No argument or `all` - Reloads everything (config, blueprints, and software)
- `config` - Reloads only the vSLS plugin configuration
- `blueprints` - Sends an API request to tell Protocube to reload blueprints, then refetches blueprints from Protocube and updates the local registry in vSLS
- `software` - Makes an API request to tell Protocube to reload the software configuration files

**Details:**
- When reloading blueprints, the command displays the number of blueprints loaded
- Reloading without arguments defaults to reloading everything
- Each reload type provides appropriate feedback messages
- Errors during reload are displayed with failure reasons

---

## Stop Command

**Permission:** `sls.command.admin`

**Description:** Gracefully stops a server or all servers. This performs a proper shutdown, allowing the server to save data and shut down cleanly.

**Usage:**
```
/sls stop <server>
/sls stop all
/sls stop <server> force
/sls stop all force
```

**Arguments:**
- `server` - The server ID to stop
- `all` - Stops all running servers
- `force` (optional) - Forces server unregistration even if shutdown fails

**Details:**
- The `stop` command performs a graceful shutdown, allowing servers to save data
- When using `all`, the command stops all currently running servers
- The `force` option ensures the server is unregistered from vSLS even if the shutdown process fails
- Without `force`, the server remains registered if shutdown fails
- If no servers are running when using `all`, an appropriate message is displayed

---

## Kill Command

**Permission:** `sls.command.admin`

**Description:** Forcefully kills a server or all servers. This immediately terminates the server process without a graceful shutdown.

**Usage:**
```
/sls kill <server>
/sls kill all
/sls kill <server> force
/sls kill all force
```

**Arguments:**
- `server` - The server ID to kill
- `all` - Kills all running servers
- `force` (optional) - Forces server unregistration even if kill fails

**Details:**
- The `kill` command immediately terminates the server process
- Unlike `stop`, this does not allow for graceful shutdown or data saving
- Use with caution as unsaved data may be lost
- The `force` option ensures the server is unregistered even if the kill operation fails
- If no servers are running when using `all`, an appropriate message is displayed

---

## Dequeue Command

**Permission:** `sls.command.admin` (required to dequeue other players)

**Description:** Removes the command sender or a specific player from a server queue. Players can dequeue themselves without admin permission.

**Usage:**
```
/sls dequeue
/sls dequeue [all | local | <player>]
```

**Arguments:**
- No argument - Dequeues the command sender
- `all` - Dequeues all players currently in any queue
- `local` - Dequeues all players on the same server as the command sender
- `<player>` - Dequeues a specific player by username

**Details:**
- Players can dequeue themselves without admin permission
- Admin permission is required to dequeue other players
- The command provides feedback when a player is successfully dequeued, including the server name
- If a player is not in a queue, an appropriate message is displayed
- Tab completion is available for player names

---

## Status Command

**Permission:** `sls.command.admin`

**Description:** Displays the current status of a server. The status can be fetched from the local cache or from the remote node.

**Usage:**
```
/sls status <server>
/sls status <server> remote
```

**Arguments:**
- `server` - The server ID to check the status for
- `remote` (optional) - Fetches the status from the remote node via the API

**Details:**
- Without `remote`, displays the most recently reported status from the local cache
- With `remote`, fetches the current status directly from the SLS daemon via the API
- Status values include: `offline`, `starting`, `running`, `stopping`
- The remote status should generally match the cached status unless there's a desync between vSLS and the daemon/Protocube
- If the server doesn't exist, an error message is displayed

---

## Stats Command

**Permission:** `sls.command.admin`

**Description:** Displays resource usage statistics for a server, including CPU, memory, network, and uptime information.

**Usage:**
```
/sls stats <server>
```

**Arguments:**
- `server` - The server ID to view statistics for

**Details:**
The command displays the following statistics:
- **CPU:** Current CPU usage percentage
- **Memory:** Current memory usage, maximum memory, and usage percentage
- **Network Inbound:** Incoming network traffic (formatted automatically)
- **Network Outbound:** Outgoing network traffic (formatted automatically)
- **Uptime:** How long the server has been running

All values are automatically formatted in appropriate units (KB, MB, GB, etc.) for readability.

---

## Version Command

**Permission:** None (public command)

**Description:** Displays the current plugin version of vSLS and the plugin author(s).

**Usage:**
```
/sls version
```

**Details:**
- This command does not require any permissions
- Displays the version number and author information in a formatted message
- Useful for checking which version of vSLS is installed

---

## Notes

- All commands that require `sls.command.admin` permission will display an error if executed without proper permissions
- Server IDs are case-sensitive and must match exactly
- Tab completion is available for most commands to help with argument selection
- Commands that interact with servers will display error messages if the server doesn't exist or is unavailable
- Some commands (like `console`) have built-in retry mechanisms to handle asynchronous operations

