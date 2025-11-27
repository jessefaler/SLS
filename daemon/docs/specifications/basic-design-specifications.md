# Specifications – SLS Daemon

The **SLS Daemon** is a lightweight node agent written in golang responsible for managing ephemeral game servers on a single machine.

> **Notice**
> This document is **not a complete system design specification**. It provides a simplified overview of the SLS Daemon’s expected behavior and features. Content is **subject to change** as the system evolves.


## 1. Communication and API

* The Daemon exposes an **internal gRPC server** for receiving requests from the Orchestrator.
* It also connects to the Orchestrator’s gRPC server for sending requests.
* **JWT authentication** is required for all communication between the Daemon and Orchestrator.

---

## 2. Blueprint System

* A **Blueprint** is a high-level specification of a game server environment.
* Blueprints define:

    * Server metadata
    * World configuration
    * Resource limits (CPU, memory, storage)
    * Custom configuration file patches
    * Plugins to load
    * Whether the container’s volume should be saved
    * Whether saved data should be compressed
* The Daemon can create a server instance by receiving a **blueprint ID**.
* On success, the Daemon returns a unique **server ID**.
* An Example blueprint is stored in `/blueprint/example.yaml`.

---

## 3. Server Execution

* Game servers run inside **Docker containers** in isolated environments.
* Each container uses an **overlay2 file system** within the SLS Linux namespace:

    * The server/world files and plugins are mounted to the lower directory.
    * The merged directory is mounted into the container’s volume.
    * This enables **copy-on-write**, so only modified files are stored.
    * Multiple containers can share the same base files without duplication.

---

## 4. Synchronization System

* The Orchestrator is the **source of truth** for all files.
* The Daemon maintains synchronized copies of:

    * Blueprints
    * Server files
    * World files
    * Plugins
* Sync behavior:

    * When a new or updated file is available, the Orchestrator notifies nodes.
    * The Daemon pulls and applies the changes.
    * On first connection, the Daemon verifies all files to ensure consistency.
    * File syncing can be enabled or disabled in the configuration.