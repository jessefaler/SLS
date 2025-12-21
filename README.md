# ![SLS Standalone Logo](https://cdn.modrinth.com/data/cached_images/7115a8404f7d6a94fd7aab586d6c4de1e9b3846c.png)

**SLS** is a **declarative orchestration system for ephemeral game servers**, designed especially for Minecraft networks.
Everything in SLS is reproducible, isolated, and fully defined through Blueprints.

SLS runs every game server inside its own **Docker container**, keeping environments clean, consistent and secure.

SLS is **free and open-source** software licensed under the **AGPL-3.0**.

## Components

### **Protocube**

The master controller for all SLS operations. Responsible for building, orchestrating, and managing servers based on Blueprints.

### **Daemon**

The server control plane for SLS. Each daemon runs containers, handles world mounting, applies overlays, and executes lifecycle actions.

### **S4J**

A Java wrapper for the Protocube API, providing a clean interface for plugins and external tools.

## Extras

### **vSLS**

A Velocity proxy plugin that lets you manage SLS servers directly through in-game commands.

### **SlimePacks**

A resource-pack management system with automatic routing and versioning logic.

## Blueprints

Blueprints are **declarative specifications** describing *exactly* how a game server should run:
its software, world, version, limits, configuration patches, and bundled content.

Below is a full example Blueprint.

```yaml
# Blueprint metadata
blueprint:
  id: 'block_hunt'               # Unique slug ID
  name: 'Block Hunt'             # Human-readable name
  type: 'minigame'               # Arbitrary grouping tag

# World configuration
world:
  name: "Block Hunt"
  authors: "Protoxon"
  path: "minigames/block_hunt"

# Server configuration
server:
  software: "Paper"
  version: "1.18.2"
  image: "sls:java_21"
  path: "paper/1.18.2"

  # Optional resource limits
  limits:
    memory_limit: 4096
    swap: 1024
    io_weight: 500
    cpu_limit: 200
    disk_space: 10000
    threads: ""
    oom_disabled: false

  # Optional configuration patches
  configs:
    server.properties:
      parser: properties
      find:
        motd: "Block Hunt Server"

  # Optional content bundled with the server.
  content:
    - name: "WorldEdit"
      source: "plugins/bukkit/worldedit"
    - name: "Terralith"
      source: "datapacks/terralith"
    - name: "Distant Horizons"
      source: "mods/fabric/distant_horizons"

# Whether to persist servers created from this blueprint.
# If false, the server is removed on shutdown.
saving: false

# Arbitrary metadata for external systems
annotations:
  resource_pack: "block_hunt"
  allowed_clients: "1.12..1.15, !1.13, 1.20"
  maintainer: "Protoxon"
  tags: ["hide n seek", "pvp"]
```

## Development Status

> [!WARNING]
> This version of SLS is **actively in development**.
> For a production-ready experience, use any release prior to **`4.0.0`**.
> The source code for previous stable versions is available on the `v3.0.0` branch.
