# ![SLS Standalone Logo](https://cdn.modrinth.com/data/cached_images/7115a8404f7d6a94fd7aab586d6c4de1e9b3846c.png)

**SLS** is a system for managing networks of **ephemeral game servers**, designed for games like **Minecraft, Hytale, Garry's Mod, and Rust**. It lets you **spin up and tear down servers quickly**, with each server **fully isolated and reproducible**.

For full documentation and examples view **[SLS Documentation](https://protoxon.github.io/sls-docs/)**.

Discord: [![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.com/invite/BrH8GtyGSh)

[![Star this repo](https://img.shields.io/badge/If%20you%20like%20this%20project-Star%20it%20on%20GitHub-ffdd00?style=for-the-badge&logo=github)](https://github.com/jessefaler/SLS)
---

Servers are defined using [**Blueprints**](https://protoxon.github.io/sls-docs/guide/blueprints/introduction.html), where you can specify everything from:

* Server version and resource limits
* Volumes mounts
* Config patches
* Custom configuration

From a single [Blueprint](https://protoxon.github.io/sls-docs/guide/blueprints/introduction.html), you can create **as many server instances as needed**.

## Key Components

* [**Protocube**](https://protoxon.github.io/sls-docs/guide/installing-protocube.html): The **core controller** of SLS. Tracks all servers, provides an API for management, and routes requests to backend nodes.
* [**Daemon**](https://protoxon.github.io/sls-docs/guide/installing-daemon.html) (nodes): The **server control plane**. Runs on each physical machine, connects to Protocube, and manages servers inside Docker containers.
  ### Extra:
* [**S4J**](https://protoxon.github.io/sls-docs/reference/api/): A **Java API wrapper** for the SLS API.

* [**vSLS**](https://protoxon.github.io/sls-docs/guide/vsls/): vSLS is a [Velocity](https://docs.papermc.io/velocity/) plugin that lets you manage SLS servers in game.
---

> SLS is ideal for networks that need **fast, consistent, and isolated server environments**. Whether you're running **minigames**, **private worlds**, or **large server networks**, SLS gives you **full control** over how servers are **created, configured, and managed**.

## Features

* **Isolated & Reproducible Environments**
  Every server runs in its own Docker container and is created exactly as defined in its [Blueprint](https://protoxon.github.io/sls-docs/guide/blueprints/introduction.html) ensuring consistency across every instance.

* **Zero-Copy Instancing (COW)**
  Avoid repeated file copying and reinstallations. SLS uses copy-on-write mounts to efficiently create new servers with minimal overhead.

* **Horizontal Scaling**
  Easily scale your network by adding more nodes. SLS automatically utilizes available resources across your infrastructure.

* **Real-Time Status & Events**
  Track server lifecycle states (starting, online, stopping) and subscribe to an event stream for real-time updates.

* **Built-in Load Distribution**
  Servers are automatically distributed across nodes to balance load and optimize resource usage.

* **Custom Software Support**
  Define and run your own server types using flexible [software configurations](https://protoxon.github.io/sls-docs/guide/software-configurations/introduction.html).

* **HTTP API Control**
  Fully manage servers programmatically through a simple and powerful [HTTP API](https://protoxon.github.io/sls-docs/reference/api/).

## Demo

A live demo **Minecraft** network is available if you want to try SLS in action.

* **Server address:** `demo.protoxon.com`

This demo showcases how servers can be dynamically created and managed in a real environment.

The demo utilizes the [**vSLS**](https://protoxon.github.io/sls-docs/guide/vsls/) plugin.

## More Information

For full documentation, examples, view **[SLS Documentation](https://protoxon.github.io/sls-docs/)**.

## Example Blueprint

> **Note:** This is a simplified example Blueprint. It does not include all available sections. For full details, see: [Blueprint Docs](https://protoxon.github.io/sls-docs/guide/blueprints/introduction.html)

```yaml
blueprint:
  id: "slsmp1"
  name: "SLSMP1"
  type: "survival"

server:
  software: "paper"
  version: "1.21.11"
  
state:
  volumes:
    - name: "world" 
      source: "worlds/survival/SLSMP1"
      target: "/world"
      mode: cow
```


