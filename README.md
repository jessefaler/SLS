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
  id: "blueprint"                # Unique slug ID
  name: "Blueprint Name"         # Human-readable name
  type: "game"                   # Arbitrary grouping tag

# Declarative server state
# Volumes are managed storage units.
# All volume sources are resolved relative to the sls
# configured volumes directory (e.g. /sls/volumes).
#
# Volumes cannot escape this directory and are never arbitrary host paths.
state:
  volumes:
    # Primary server filesystem
    - name: "world"
      source: "worlds/world"     # Resolved to /sls/volumes/worlds/world
      mount: "/world"            # Mount point inside the container
      mode: cow                  # cow | ro | rw (default: cow)

    # Read-only plugins or assets
    - name: "plugins"
      source: "plugins"
      mount: "/plugins"
      mode: ro

    # Example persistent data volume
    # (useful for databases, player data, etc.)
    - name: "data"
      source: "shared/data"
      mount: "/data"
      mode: rw

# Server runtime configuration
# The server base files are automatically installed or pre-installed
# at /servers/<software>/<version> (or custom path).
server:
  software: "platform"
  version: "1.0.0"
  image: "sls:java_21"

  # optional override
  # path: "custom/path"

  # Resource limits applied to the container
  limits:
    memory_limit: 4096           # MB
    swap: 1024                   # MB
    io_weight: 500
    cpu_limit: 200               # Percentage (200 = 2 cores)
    disk_space: 5120             # MB
    threads: ""
    oom_disabled: false

  # Configuration patches applied at startup
  configs:
    server.properties:
      parser: properties
      find:
        motd: "Blueprint Server"

# Explicit host mounts
# These mount arbitrary host paths directly into the container.
# They must be explicitly allowed in the daemon configuration.
# (ro = read-only) omit to allow writing to files
mounts:
  - /host/path:/home/container:ro

# Copy files into server filesystem at creation
# - Files inside the SLS folder can always be copied
# - Files outside the SLS folder require the source path to be listed in the daemon's allowed_mounts
# Destinations are always relative to the server filesystem
copy:
  - sls/files/config.yml:plugins/config.yml

# Whether servers created from this blueprint persist after shutdown.
# If false, the server instance and all non-persistent volume state
# are destroyed when the server stops.
save: false

# Arbitrary metadata for external systems
annotations:
  maintainer: "Maintainer"
  tags: ["game", "example"]
```

## Development Status

> [!WARNING]
> This version of ```SLS``` is in **active development** and is not yet ready for use.

## Daemon Banner
<img width="734" height="257" alt="Screenshot From 2026-02-02 17-57-48" src="https://github.com/user-attachments/assets/b9a0bdaa-378b-477d-9bd9-58a09672092b" />


