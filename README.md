# ![SLS Standalone Logo](https://cdn.modrinth.com/data/cached_images/7115a8404f7d6a94fd7aab586d6c4de1e9b3846c.png)

**SLS** is a **declarative orchestration system for ephemeral game servers**, designed especially for server networks.
Everything in SLS is reproducible, isolated, and fully defined through Blueprints.

SLS runs every game server inside its own **Docker container**, keeping environments clean, consistent and secure.

SLS is **free and open-source** software licensed under **AGPL-3.0**.

### **Protocube**

Protocube is the core controller for SLS, responsible for managing all server instances across the system. It provides a REST API for creating, managing, and monitoring servers.

**Docs:**
- [API Docs](#) _(Coming Soon)_
- [Plugin Docs](#) _(Coming Soon)_
- [Setup](#) _(Coming Soon)_

### **Daemon**

The server control plane for SLS. Each Daemon is responsible for provisioning, controlling and monitoring servers inside isolated Docker containers, exactly as defined by their Blueprints.

### **S4J**

A Java wrapper for the Protocube API, providing a clean interface for plugins and external tools.

## Blueprints

Blueprints are **declarative specifications** describing *exactly* how a game server should run:
its software, world, version, limits, configuration patches, and bundled content.

Below is a full example Blueprint.

```yaml
# Blueprint metadata
blueprint:
  id: 'blueprint'                # Unique slug ID
  name: 'Blueprint Name'         # Human-readable name
  type: 'game'                   # Arbitrary grouping tag

# World configuration
world:
  name: "Blueprint World"
  authors: "Author"
  path: "blueprint_world"

# Server configuration
server:
  software: "platform"
  version: "1.0.0"
  image: "sls:java_21"

  # Resource limits
  limits:
    memory_limit: 4096
    swap: 1024
    io_weight: 500
    cpu_limit: 200
    disk_space: 5120
    threads: ""
    oom_disabled: false

  # Configuration patches
  configs:
    server.properties:
      parser: properties
      find:
        motd: "Blueprint Server"

# Additional folders to include in the server directory. 
# These are copy-on-write mounts and will be mounted at the root 
# of the server folder. 
# 
# To have a folder appear as a subdirectory in the server, 
# create a folder with the desired name on the host and place 
# your files or subfolders inside it. 
# For example, to include something in the "plugins" folder, 
# create a folder named "plugins" on the host, place your 
# plugin folder inside it, and mount the directory container 
# container your "plugins" here.
  content:
    - name: "WorldEdit"
      source: "platform/data"
  
  # Additional mount points for the server container.
  # Specify mounts in the format: HostPath:ContainerPath:ro
  # (ro = read-only)
  #
  # Important: The mount must be permitted in the daemon's configuration
  # file for it to function. For example:
  #
  # system:
  #   allowed_mounts:
  #     - /host/path
  mounts:
    - /host/path:/home/container

# Whether to persist servers created from this blueprint.
# If false, the server is deleted on shutdown.
save: false

# Arbitrary metadata for external systems
annotations:
  maintainer: "Maintainer"
  tags: ["game", "example"]
```

## Development Status

> [!WARNING]
> This version of ```SLS``` is in **active development** and is not yet ready for use.
