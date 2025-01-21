
---

# **SLS Plugin Architecture Documentation**

**SLS** is a Velocity plugin that operates on the proxy to manage the entire server network. It handles tasks ranging from server initialization and management to player transfers and automatic server startup.

---

## **Table of Contents**
1. [ServerInstance](#serverinstance)
    - [Server Process Creation](#server-process-creation)
    - [Key Features](#key-features-of-serverinstance)
    - [Startup Parameters](#server-startup-parameters)
2. [Server Registry](#server-registry)
    - [Structure and Organization](#structure-and-organization)
    - [Key Features](#key-features-of-the-server-registry)
3. [Server World Accessing and Saving](#server-world-accessing-and-saving)
    - [Key Advantages](#key-advantages)
    - [How OverlayFS Works](#how-overlayfs-works-in-sls)
    - [OverlayFS Integration and Linux Dependency](#overlayfs-integration-and-linux-dependency)
4. [Summary of Benefits](#summary-of-benefits)

---

## **ServerInstance**

The `ServerInstance` class represents each individual server managed by the plugin. These servers run asynchronously on their own threads using `CompletableFuture`, separate from the proxy.

### **Server Process Creation**

Each server instance is created with a `ProcessBuilder`, defining essential parameters such as:

- **JDK**: Specifies the Java Development Kit version.
- **Startup and Runtime Memory**: Allocates memory for the server.
- **Server Jar**: Identifies the server `.jar` file to execute.
- **Port Number**: Assigns a port number for network communication.

### **Key Features of ServerInstance**

- **Instantiation**: `ServerInstance` allows for creating server instances, which are managed and stored in a registry. For more details, refer to the [Server Registry](#server-registry).
- **Control Methods**: Provides methods to control server states (e.g., `start`, `stop`).

### **Server Startup Parameters**

A `ServerInstance` is initialized with three main parameters:

1. **World Object**: Encapsulates world-related data. See [World Documentation](#world) for more details.
2. **Namespace Object**: Groups servers for organizational context. See [Namespace Documentation](#namespace).
3. **Server Name**: A string representing the server's name.

---

## **Server Registry**

The **Server Registry** is a core component of **SLS**, managing all active `ServerInstance` objects. It organizes these instances by **namespace** and **server name**.

### **Structure and Organization**

The registry is a multi-layered `HashMap`, structured as follows:

1. **Namespace (First Layer)**: Uses namespaces as keys, organizing servers into related groups.
2. **Server Instances (Second Layer)**: Each namespace contains servers, categorized by unique names, with the server name as the key.

### **Key Features of the Server Registry**

- **Efficient Server Management**: Servers can be managed by specifying their namespace and name.
- **Namespace-based Organization**: Logically separates servers into groups for easier management.
- **Access and Control**: Methods for:
    - Starting new servers.
    - Accessing active servers.
    - Managing individual servers (e.g., stop, restart, modify).

---

## **Server World Accessing and Saving**

**SLS** optimizes server world management using **OverlayFS**, a union file system with a **Copy-on-Write (COW)** mechanism. This reduces storage usage and improves performance while preventing race conditions.

### **Key Advantages**

1. **Improved Performance**: Servers initialize quickly without waiting for full world copies.
2. **Optimized Storage Usage**: Servers share a base copy of the world, storing changes in separate write layers.
3. **Avoiding Race Conditions**: Isolates server modifications, preventing conflicts between servers accessing the same world.

### **How OverlayFS Works in SLS**

- **Read-Only Layer**: The base copy of each world is stored in a read-only layer, accessible by all servers.
- **Write Layer**: Each server stores its modifications in a unique write layer, preserving the base world.

### **OverlayFS Integration and Linux Dependency**

OverlayFS is Linux-specific, so **SLS** must run on a Linux environment. Alternatively, **Docker** can provide a Linux environment for non-Linux hosts.

### **Summary of Benefits**

- **Faster Startup Times**: Near-instant initialization due to shared world access.
- **Lower Storage Costs**: Minimizes disk usage by only storing changes.
- **Concurrency Control**: Servers safely modify the same world without conflicts.

OverlayFS ensures fast performance, efficient resource usage, and scalability for server networks in **SLS**.

**Why OverlayFS?** OverlayFS natively supports copy-on-write, making it the most efficient option available. In contrast, UnionFS uses a copy-up mechanism, which duplicates the entire file to the write layer instead of just the changes, resulting in less efficiency.


---