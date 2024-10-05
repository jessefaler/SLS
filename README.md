# SLS Plugin (Development Branch)

Welcome to the development branch of the SLS plugin, which features an ongoing partial rewrite aimed at introducing new functionalities and improvements.

## New Features

### Dynamic Registries
Registries are now dynamic, allowing the creation of unlimited registries. Refer to the `template.yml` file in the `resources` folder for the new format. These registries are managed by a central **Registry Manager**, which handles and tracks all created registries.

### Structural Changes
- **Separation of Servers and Worlds**: 
  Servers are now decoupled from worlds. Each registry has its own directory containing world folders, while a separate universal `servers` folder houses the version directories of the servers.
- **Server Version Assignment**: 
  In the registry config, under each world, you can specify which server version to use.
- **Namespace-Based World Storage**: 
  Worlds are now saved under the `namespaces` directory, organized by namespace names.

### Namespaces
Namespaces are a way to organize and run multiple servers independently. They allow:
- **Multiple Servers per Namespace**: You can run multiple instances of the same server within one namespace, with server names automatically incremented (e.g., `makers_punch(1)`).
- **Unlimited Servers**: No limit to how many servers you can start under one namespace.
- **Custom World Versions**: Each namespace can maintain its own version of the world, with options to create new worlds of types such as "superflat," "void," and "default."

### Server World Access and Saving
The SLS plugin optimizes server world management through **OverlayFS**, a union file system that uses Copy-on-Write (COW) to reduce storage usage, improve performance, and prevent race conditions.

**Key Benefits**:
- **Improved Performance**: Servers initialize quickly without waiting for full world copies.
- **Optimized Storage**: Servers share a base world copy, with changes stored in separate layers.
- **Avoids Race Conditions**: Each server operates in its own isolated write layer, preventing conflicts.

**How OverlayFS Works in SLS**:
- **Read-Only Layer**: The base world copy is stored in a read-only layer accessible by all servers.
- **Write Layer**: Each server stores modifications in its own write layer, preserving the integrity of the base world.

**OverlayFS Integration**:  
OverlayFS is a Linux-specific feature, so SLS must run in a Linux environment. Non-Linux hosts can use Docker to provide the required Linux environment.

### World Class
The `World` class represents a single world configuration. It includes all the details listed in the registry configuration, along with default values and paths to the world and server directories.

### Color Class
An enum for ANSI color codes, providing constants for various text colors and styles.

### Memory Converter Class
A utility class for converting string representations of memory sizes into integers (megabytes).

## Documentation
New **documentation on architecture** and **legal documents related to domain usage** are being developed. Stay tuned for updates.

## Potential Future Features
The following features are under consideration for future updates:
- **Automatic Resource Pack Versioning**
- **Automatic New Server Creation**

## Unit Testing
Unit tests have been added to validate key methods in the plugin. There are also in-game commands available for testing server startups and player connections.

## Additional Information
- **Configuration Options**: New configuration options have been added to world configurations. Refer to the `template.yml` file in the resources folder for details.
- **Error Handling**: Improved error handling throughout the plugin.
- **Code Refinement**: Existing classes have been refactored for improved performance, readability, and error handling. JavaDocs have been added where necessary.

