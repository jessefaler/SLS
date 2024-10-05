## SLS: Server Network Manager Plugin

SLS is a Velocity plugin designed to be installed on a proxy server, serving as a comprehensive server network manager. It handles all aspects of server operations, including:

- Server configuration and startup
- Server instance management
- World organization
- Player transfers between servers

### Key Features

1. **Registries for World and Server Management**  
   SLS uses registries to group world types and track existing worlds. It also maintains a registry for active server instances, enabling efficient tracking and management of running servers.

2. **Namespaces for Server Organization**  
   By utilizing namespaces, SLS groups multiple servers into organized categories, enabling easy management and separation of running instances. This allows multiple servers of the same type to run concurrently.

3. **Custom Configurations for Each World**  
   Each registry comes with its own configuration file, which contains the specific settings for each world. This ensures flexibility in managing various world types and server environments.

4. **Automatic Server Startup**  
   When a player attempts to access a server, SLS can automatically start the server if it is not already running, ensuring a seamless experience for players.

SLS simplifies server management for proxy-based networks, making it easier to organize, scale, and control multiple servers in a unified environment.
