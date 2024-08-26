
---

# SLN plugin

## Overview
A plugin that dose things

## Commands

Here’s how you can create a dropdown menu for the command details using HTML details and summary tags within the Markdown:

---

## Commands

<details>
<summary>/sln join</summary>

**Description:**  
Starts a server and sends the player to it.

**Usage:**  
`/sln join <[minigame][archive][adventure]> <(server name)> <player|all|local>`

**Arguments:**
- `<[minigame][archive][adventure]>`: The registry to use.
- `<(server name)>`: The name of the server to join.
- `<player|all|local>`: The player(s) to send to the server (leave blank to send yourself).

</details>

---

You can add more commands following the same format. The dropdown will be collapsible, making the page easier to navigate.

### `/anothercommand`
**Description:**  
Executes another command.  

**Usage:**  
`/anothercommand [optionalArg]`

**Arguments:**
- `[optionalArg]`: Optional argument.

**Permissions:**  
- `plugin.anothercommand`: Grants access to use this command.

## Usage

## Installation

1. **Download the Latest Jar:**
   - Obtain the latest version of the SLS plugin [here](https://github.com/protoxon/SLS/releases).
   
2. **Move Jar to Plugins Folder:**
   - Move the downloaded jar file to the `plugins` folder of your proxy server.
   
3. **Start the Server:**
   - Launch your proxy server to generate the configuration files.
   
4. **Modify the Configurations:**
   - Adjust the configuration settings as needed to suit your server setup. The configuration files can be found in the `plugins/sls` directory.

### Configuration
- After installation, a configuration file will be generated in the `plugins/pluginname` directory.
- Modify the configuration to fit your server's needs.
- Use `/reload` to apply configuration changes without restarting the server.

### Basic Usage
- Use `/examplecommand` to perform the basic action of the plugin.
- Adjust permissions using your permissions plugin to control who can access each command.

## Permissions

- **Administrator Commands:** 
  - Permission: `sls.command.admin`
  - Required for executing administrative commands on the proxy server.

## Common Issues

### Plugin Not Loading
- **Cause:** The plugin may not be compatible with your server version.
- **Solution:** Ensure you are using a supported version of Minecraft. Check the [Releases](../releases) page for compatibility information.

### Commands Not Working
- **Cause:** Missing permissions or incorrect command syntax.
- **Solution:** Double-check the command syntax and ensure the appropriate permissions are granted.

### Configuration Changes Not Applying
- **Cause:** The server may not have been reloaded after making changes.
- **Solution:** Use the `/reload` command or restart your server after editing the configuration.

---
