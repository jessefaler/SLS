# ![SLS Standalone Logo](https://cdn.modrinth.com/data/cached_images/7115a8404f7d6a94fd7aab586d6c4de1e9b3846c.png)

## Overview
SLS is a powerful plugin designed to dynamically start and manage servers, facilitate cross-server communication, and maintain a server registry.

---

<details>
<summary><h2><strong>Commands</strong></h2></summary>

> <details>
> <summary><strong>/sls join</strong></summary>
> 
> **Description:**  
> Starts a server and sends the player to it.
> 
> **Usage:**  
> `/sls join <[minigame][archive][adventure]> <(server name)> <player|all|local>`
> 
> **Arguments:**
> - `<[minigame][archive][adventure]>`: The registry to use.
> - `<(server name)>`: The name of the server to join.
> - `<player|all|local>`: The player(s) to send to the server (leave blank to send yourself).
> ---
> </details>
> 
> <details>
> <summary><strong>/sls start</strong></summary>
> 
> **Description:**  
> Allows anyone with the `sls.command.admin` permission to starts a server.
> 
> **Usage:**  
> `/sls start <[minigame][archive][adventure]> <(server name)>`
> 
> **Arguments:**
> - `<[minigame][archive][adventure]>`: The registry to use.
> - `<(server name)>`: The name of the server to start.
> ---
> </details>
> 
> <details>
> <summary><strong>/sls shutdown</strong></summary>
> 
> **Description:**  
> Allows anyone with the `sls.command.admin` permission to shut down a server.
> 
> **Usage:**  
> `/sls shutdown <(server name)|all>`
> 
> **Arguments:**
> - `<(server name)>`: The name of the server to shut down.
> ---
> </details>
> 
> <details>
> <summary><strong>/sls info</strong></summary>
> 
> **Description:**  
> Allows anyone with the `sls.command.admin` permission to list all the online servers and their player counts.
> 
> **Usage:**  
> `/sls info`
>
> ---
> </details>
>
> <details>
> <summary><strong>/sls debug</strong></summary>
> 
> **Description:**  
> Shows virtual console output via in-game taskbar.
> 
> **Usage:**  
> `/sls debug`
>
> ---
> </details>
>
> <details>
> <summary><strong>/sls console</strong></summary>
> 
> **Description:**  
> Allows anyone with the `sls.command.admin` permission to send console commands in-game.
> 
> **Usage:**  
> `/sls console <[console command]>`
>
> ---
> </details>
>
> <details>
> <summary><strong>/sls config</strong></summary>
> 
> **Description:**  
> Allows anyone with the `sls.command.admin` permission to view plugin configurations in-game.
> 
> **Usage:**  
> `/sls config <[reload]|[view]> <(server name)|(registry name)>`
> 
> **Arguments:**
> - `<[reload]>`: Reloads the config file of the given registry
> - `<[view]>`: View the config file of the given server.
> - `<(server name)>`: The name of the server to view.
> - `<(registry name)>`: The name of the registry to reload.
> ---
> </details>

</details>

---

<details>
<summary><h2><strong>Installation</strong></h2></summary>

1. **Download the Latest Jar:**  
   Obtain the latest version of the SLS plugin [here](https://github.com/protoxon/SLS/releases).

2. **Move Jar to Plugins Folder:**  
   Move the downloaded jar file to the `plugins` folder of your proxy server.
   
3. **Start the Server:**  
   Launch your proxy server to generate the configuration files.
   
4. **Modify the Configurations:**  
   Adjust the configuration settings as needed to suit your server setup. The configuration files can be found in the `plugins/sls` directory.

</details>

---

<details>
<summary><h2><strong>Adding Servers</strong></h2></summary>

1. **Choose a Registry:**  
   Select the registry where you want to add your server. The options are `minigames`, `archive`, or `adventure`.

2. **Set Up Server Files:**  
   Navigate to the corresponding folder within the `sls` config directory.  
   Create a new folder for your server inside this registry folder.  
   Place all your server files in this new folder and ensure that the server's JAR file is named `server.jar`.

3. **Update the Config File:**  
   Open the configuration file for the chosen registry.  
   Follow the existing format in the config file to add your server.

4. **Reload the Server List:**  
   In-game, run the command `/sls reload`.  
   Your server should now appear in the list.

5. **Join the Server:**  
   To join the server, use the command `/sls join <registry> <server_name>`.

</details>

---

<details>
<summary><h2><strong>Permissions</strong></h2></summary>

- **Administrator Commands:**  
  - **Permission:** `sls.command.admin`  
  - **Required for:** Executing administrative commands on the proxy server.

</details>

---

<details>
<summary><h2><strong>Common Issues</strong></h2></summary>

> <details>
> <summary><strong>Plugin Not Loading</strong></summary>
> 
> **Cause:** The plugin may not be compatible with your server version.  
> **Solution:** Ensure you are using a supported version of Minecraft. Check the [Releases](https://github.com/protoxon/SLS/releases) page for compatibility information.
> 
> </details>
> 
> <details>
> <summary><strong>Commands Not Working</strong></summary>
> 
> **Cause:** Missing permissions or incorrect command syntax.  
> **Solution:** Double-check the command syntax and ensure the appropriate permissions are granted.
> 
> </details>
> 
> <details>
> <summary><strong>Configuration Changes Not Applying</strong></summary>
> 
> **Cause:** The server may not have been reloaded after making changes.  
> **Solution:** Use the `/sls reload` command or restart your server after editing the configuration.
> 
> </details>

</details>

---
