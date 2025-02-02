# ![SLS Standalone Logo](https://cdn.modrinth.com/data/cached_images/7115a8404f7d6a94fd7aab586d6c4de1e9b3846c.png)

**SLS** is a powerful plugin designed to dynamically start and manage servers, facilitate cross-server communication, and maintain a server registry.

> [!WARNING]  
> This version of SLS is in active development and may contain bugs or incomplete features.  
> For a stable experience, use any release prior to `4.0.0`.  
> The source code for previous stable versions is available in the `legacy-code` branch.

## Latest Update
Release 4.0.0-beta: First release with Pterodactyl integration

This release introduces major changes with the integration of Pterodactyl for server management. Pterodactyl enables running servers on separate machines using Wings, providing enhanced scalability, improved security, and better performance. It also allows the separation of proxy and game servers onto different machines.

In addition to Pterodactyl integration, this version introduces several long-awaited features, including:

- Support for running multiple instances of the same world
- Dynamic registries
- World saving and creation of custom worlds on the fly
- Numerous minor upgrades and quality-of-life improvements

##
If you’d like to see the SLS plugin in action, join **slimelabs.net** and enter `/sls` in chat. The server is currently running sls version **2.1.2**. *Note: Administrator features are hidden.*

## Features
- **Dynamic Server Management:** Automatically start and stop servers as needed.
- **Cross-Server Communication:** Seamlessly manage communication between multiple servers.
- **Server Registry Management:** Keep track of active and available servers.

## Installation

1. **Download the Latest Jar:**
   - Obtain the latest version of the SLS plugin [here](https://github.com/protoxon/SLS/releases).
   
2. **Move Jar to Plugins Folder:**
   - Move the downloaded jar file to the `plugins` folder of your proxy server.
   
3. **Start the Server:**
   - Launch your proxy server to generate the configuration files.
   
4. **Modify the Configurations:**
   - Adjust the configuration settings as needed to suit your server setup. The configuration files can be found in the `plugins/sls` directory.

## Permissions

- **Administrator Commands:** 
  - Permission: `sls.command.admin`
  - Required for executing administrative commands on the proxy server.
