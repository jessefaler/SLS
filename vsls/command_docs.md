# vSLS commands

Reference for the in-game **`/sls`** command tree. Unless noted, arguments are case-sensitive and must match the server or blueprint identifiers returned by the plugin.

## Table of contents

- [Info](#info)
- [List](#list)
- [Create](#create)
- [Start](#start)
- [Pause](#pause)
- [Resume](#resume)
- [Restart](#restart)
- [Stop](#stop)
- [Kill](#kill)
- [Reset](#reset)
- [Join](#join)
- [Find](#find)
- [Delete](#delete)
- [Console](#console)
- [Logs](#logs)
- [Install](#install)
- [Blueprint](#blueprint)
- [Mixin](#mixin)
- [Node](#node)
- [System](#system)
- [Reload](#reload)
- [Status](#status)
- [Stats](#stats)
- [Debug](#debug)
- [Dequeue](#dequeue)
- [Version](#version)
- [General notes](#general-notes)

---

## Info

**Permission:** `sls.command.admin` (required for server-specific details)

**Description:** Shows active servers and player counts, or detailed information for one server.

**Usage:**

```
/sls info
/sls info <server>
```

**Details:**

- `/sls info` — Lists active servers with player counts.
- `/sls info <server>` — Detailed view, including:
  - Player count (hover for player names)
  - Status, blueprint id, blueprint type
  - Server software and version
  - Resource stats: CPU, memory (current / max and %), network in/out, uptime

---

## List

**Permission:** None

**Description:** Lists every server vSLS knows about. Each line shows display name, status (by name color), and player count. Hover the name for the composite id; hover the player count for names on that server.

**Usage:**

```
/sls list
```

---

## Create

**Permission:** `sls.command.admin`

**Description:** Provisions a new server from a blueprint and starts it. Optional `key=value` flags override node, resources, software/image, and config patches.

**Usage:**

```
/sls create <blueprint_type> <blueprint_id>
/sls create <blueprint_type> <blueprint_id> <flags...>
```

**Arguments:**

| Argument | Meaning |
|----------|---------|
| `blueprint_type` | Blueprint type (for example `minigame`, `adventure`, `pvp`). |
| `blueprint_id` | Specific blueprint id. |
| `flags...` | Optional space-separated overrides, each `--name=value` (see table below). |

**Override flags:**

| Flag | Value | Effect |
|------|--------|--------|
| `--node=` | Node id | Create on this node; short ids resolve against the API node list. |
| `--save=` | `true` / `false` | Enable or disable saving for the instance. |
| `--cpu=` | Integer | CPU limit (percentage of CPU the instance may use). |
| `--memory=` | Integer | Memory limit in **mebibytes (MiB)**. |
| `--swap=` | Integer | Extra swap for the container. |
| `--io_weight=` | Integer | Relative I/O weight. |
| `--disk_space=` | Integer | Disk allowance in **megabytes (MB)**. |
| `--threads=` | String | CPU threads the Docker instance may use. |
| `--oom_disabled=` | `true` / `false` | If `true`, disables the OOM killer for the container. |
| `--software=` | String | Software id to run. |
| `--version=` | String | Software version. |
| `--image=` | String | Container image. |
| `--seed=` | String | Patches `server.properties`: `level-seed`. |
| `--view-distance=` | String | Patches `server.properties`: `view-distance`. |
| `--enable-command-block=` | String | Patches `server.properties`: `enable-command-block` (`true` / `false`). |

**Details:**

- Tab completion for blueprint types and ids; after the id, completion can suggest flags, node ids, and boolean values where applicable.
- `--seed`, `--view-distance`, and `--enable-command-block` are merged into one config patch.
- On success, the created server’s composite id is shown; failures print a reason.

---

## Start

**Permission:** `sls.command.admin`

**Description:** Starts an existing server that vSLS already tracks (for example after a stop, or when save kept the instance).

**Usage:**

```
/sls start <server>
```

**Arguments:**

| Argument | Meaning |
|----------|---------|
| `server` | Server id (short ids suggested by tab completion). |

**Details:**

- Does not create a new instance from a blueprint — use **Create** or **Join** for that.
- Fails with “No such server” if the id is unknown to vSLS.

---

## Pause

**Permission:** `sls.command.admin`

**Description:** Pauses a running server.

**Usage:**

```
/sls pause
/sls pause <server>
```

**Details:**

- With no argument (players only): pauses the SLS server the sender is currently on.
- Console must pass `<server>`.

---

## Resume

**Permission:** `sls.command.admin`

**Description:** Resumes a paused server.

**Usage:**

```
/sls resume
/sls resume <server>
```

**Details:**

- With no argument (players only): resumes the SLS server the sender is currently on.
- Console must pass `<server>`.

---

## Restart

**Permission:** `sls.command.admin`

**Description:** Restarts a server and queues connected players to rejoin when it reaches **starting**.

**Usage:**

```
/sls restart
/sls restart <server>
```

**Details:**

- With no argument (players only): restarts the SLS server the sender is currently on.
- Players on the server get a title/chat notice and are reconnected when ready (status listener times out after about 2 minutes).
- Console must pass `<server>`.

---

## Stop

**Permission:** `sls.command.admin`

**Description:** Graceful shutdown for one server or all servers.

**Usage:**

```
/sls stop
/sls stop <server>
/sls stop all
/sls stop <server> force
/sls stop all force
```

**Arguments:**

| Argument | Meaning |
|----------|---------|
| *(none)* | Players only: stop the SLS server the sender is on. |
| `force` | Unregister from vSLS even if shutdown fails. |

**Details:**

- Without `force`, a failed shutdown can leave the server registered.
- `all` with no running servers prints an appropriate message.
- Console must pass a server id or `all`.

---

## Kill

**Permission:** `sls.command.admin`

**Description:** Force-terminates a server (or all). Not a clean save; use **Stop** when you need a graceful exit.

**Usage:**

```
/sls kill
/sls kill <server>
/sls kill all
/sls kill <server> force
/sls kill all force
```

**Details:**

- Same `force` semantics as **Stop** for unregistering on partial failure.
- With no argument (players only): kills the SLS server the sender is on.
- Console must pass a server id or `all`.

---

## Reset

**Permission:** `sls.command.admin`

**Description:** Wipes saved overlay/world data for a server, then brings it back up and queues connected players to rejoin.

**Usage:**

```
/sls reset
/sls reset <server>
```

**Details:**

- Stops the instance, clears overlay upper/work data, starts again, and reconnects players when status becomes **starting**.
- With no argument (players only): resets the server the sender is currently on.
- Console must pass `<server>`.
- Not every server can be reset; unsupported targets report an error.

---

## Join

**Permission:** `sls.command.admin` to connect **other** players; joining **yourself** does not require admin.

**Description:** Creates the server if needed, starts it, waits until status is **ready**, then moves the chosen player(s) to it. Or connect to the server a player is already on.

**Usage:**

```
/sls join <blueprint_type> <blueprint_id>
/sls join <blueprint_type> <blueprint_id> [all | local | <player>]
/sls join player <player>
/sls join player <player> --force
```

**Arguments:**

| Argument | Meaning |
|----------|---------|
| `blueprint_type`, `blueprint_id` | Same as **Create**. |
| *(none)* | Connects the command sender. |
| `all` | Everyone on the proxy. |
| `local` | Everyone on the same backend server as the sender. |
| `<player>` | One player by username. |
| `player <player>` | Connects the sender to the vSLS server that player is on. Respects blueprint `matchmaking.maxPlayers` for normal players. |
| `--force` | Admin only (`sls.command.admin`). Bypasses blueprint capacity for `join player`. |

**Details:**

- If an admin joins a player on a server already at `matchmaking.maxPlayers`, vSLS shows a warning with a **Join Anyway** option that runs `join player` with `--force` instead of blocking silently.

---

## Find

**Permission:** None

**Description:** Shows which vSLS server a player is currently on.

**Usage:**

```
/sls find <player>
```

---

## Delete

**Permission:** `sls.command.admin`

**Description:** Permanently removes one server or every server vSLS tracks.

**Usage:**

```
/sls delete
/sls delete <server>
/sls delete all
/sls delete <server> force
/sls delete all force
```

**Details:**

- With no argument (players only): deletes the SLS server the sender is on.
- `force` continues local unregister when the remote delete fails.
- Per-server results are reported.
- Console must pass a server id or `all`.

---

## Console

**Permission:** `sls.command.admin`

**Description:** Runs a line on a server console and tries to print captured output.

**Usage:**

```
/sls console <server> <command>
```

**Arguments:**

| Argument | Meaning |
|----------|---------|
| `server` | Target server id. |
| `command` | Console line (arguments allowed). Leading `/` is stripped. |

**Details:**

- Output is gathered by sampling logs with increasing delays (for example 100 ms / 8 lines, 800 ms / 12 lines, 3000 ms / 25 lines).
- Errors are highlighted; if nothing is captured, you may see a “no output” style message.
- Works with both legacy (`>command`) and newer console formats.

---

## Logs

**Permission:** `sls.command.admin`

**Description:** Shows paginated console log lines for a server.

**Usage:**

```
/sls logs <server>
/sls logs <server> <page>
/sls logs <server> <page> <lines>
```

**Arguments:**

| Argument | Meaning |
|----------|---------|
| `server` | Server id, or `this` for the SLS server the sender is on. |
| `page` | Page number (default `1`). |
| `lines` | Lines per page (default `50`, max `1000`). Use `max` for the maximum. |

**Details:**

- Framed output with a pagination footer.
- Invalid page numbers report the valid range.

---

## Install

**Permission:** `sls.command.admin`

**Description:** Inspects or re-runs a server’s software install job (phase, container status, install logs, reinstall).

**Usage:**

```
/sls install info
/sls install info <server>
/sls install logs <server>
/sls install logs <server> <page>
/sls install logs <server> <page> <lines>
/sls install reinstall <server>
```

**Arguments:**

| Argument | Meaning |
|----------|---------|
| `server` | Server id, or `this` for the SLS server the sender is on. |
| `page` / `lines` | Same pagination rules as **Logs** for install log output. |

**Details:**

- `info` shows install phase, container status/name, exit code, timestamps, and failure reason when present.
- `info` with no server (players only) uses the sender’s current SLS server.
- `reinstall` triggers a new install run for that server.

---

## Blueprint

**Permission:** `sls.command.admin`

**Description:** Pretty-prints one blueprint (YAML-like layout with colored keys/values). Blueprints shown are **resolved** (mixin `includes` already applied).

**Usage:**

```
/sls blueprint <blueprint_id>
```

**Details:**

- Field order matches the Protocube blueprint struct: `metadata`, `includes`, `server`, `state`, `save`, `annotations`.
- Nested sections include limits, config patches, volumes, mounts, copy, and env.

---

## Mixin

**Permission:** `sls.command.admin`

**Description:** Pretty-prints one mixin (YAML-like layout with colored keys/values). Mixins shown are **resolved** (`extends` chains already flattened).

**Usage:**

```
/sls mixin <mixin_id>
```

**Details:**

- Field order matches the Protocube mixin struct: `mixin`, `extends`, `server`, `state`, `annotations`.
- Tab completion suggests known mixin ids.

---

## Node

**Permission:** `sls.command.admin`

**Description:** Inspects a daemon node and optionally reads or changes its **drained** flag. Drained nodes are excluded from provisioning: when drained is `true`, the load balancer does not start new servers on that node.

**Usage:**

```
/sls node <id>
/sls node <id> drained
/sls node <id> drained <true | false>
```

**Arguments:**

| Argument | Meaning |
|----------|---------|
| `id` | Node identifier. You may use a short id; it is resolved against the API’s node list (tab completion suggests shortened ids). |
| `drained` | Literal keyword. With no further argument, prints whether the node is currently drained (`true` or `false`). |
| `true` or `false` | Optional, after `drained`: updates the node’s drained state on the API. |

**Details:**

- `/sls node <id>` prints detailed information for the node, including:
  - **Node metadata:** name, location, URL, current drained flag, daemon version
  - **System:** architecture, CPU threads, memory, kernel version, operating system and OS type
  - **Docker:** engine version; when available, cgroup driver/version, container counts (total, running, paused, stopped), storage driver/filesystem, and runc version. If Docker details are missing, the command indicates that they are unavailable.
- `/sls node <id> drained` displays only the drained state.
- `/sls node <id> drained <true | false>` sets the drained state and confirms the value that was sent.

---

## System

**Permission:** `sls.command.admin`

**Description:** Prints Protocube version and host information (CPU threads, memory, kernel, OS).

**Usage:**

```
/sls system
```

---

## Reload

**Permission:** `sls.command.admin`

**Description:** Reloads vSLS config and/or asks Protocube to reload blueprints (and mixins) or software definitions.

**Usage:**

```
/sls reload
/sls reload all
/sls reload config
/sls reload blueprints
/sls reload software
```

**Modes:**

| Mode | Behavior |
|------|-----------|
| *(none)* or `all` | Config + blueprints/mixins + software. |
| `config` | vSLS plugin config only. |
| `blueprints` | API reload on Protocube, then refetch and refresh the local blueprint **and** mixin registries. |
| `software` | API request to reload software configuration on Protocube. |

**Details:**

- Blueprint reload prints how many blueprints and mixins loaded.
- Errors include failure reasons from the API or plugin.

---

## Status

**Permission:** `sls.command.admin`

**Description:** Shows a server’s lifecycle status from cache or from the node.

**Usage:**

```
/sls status <server>
/sls status <server> remote
```

**Details:**

- Without `remote`: last known status in vSLS.
- With `remote`: status fetched via the API from the daemon.
- Typical values include `offline`, `starting`, `running`, `stopping`.

---

## Stats

**Permission:** `sls.command.admin`

**Description:** Resource snapshot for one server (CPU, memory, network, uptime) with human-readable units.

**Usage:**

```
/sls stats <server>
```

---

## Debug

**Permission:** `sls.command.admin`

**Description:** Toggles extra debug messages for the executing player.

**Usage:**

```
/sls debug
```

**Details:**

- Players only (not console).
- Toggles on/off per player.

---

## Dequeue

**Permission:** `sls.command.admin` to dequeue **others**; dequeuing **yourself** needs no admin node.

**Description:** Removes the sender or selected players from matchmaking queues.

**Usage:**

```
/sls dequeue
/sls dequeue [all | local | <player>]
```

**Details:**

- Tab completion for player names where applicable.
- Feedback includes the server/queue context when relevant.

---

## Version

**Permission:** None

**Description:** Prints vSLS version and author metadata.

**Usage:**

```
/sls version
```

---

## General notes

- Commands that need `sls.command.admin` fail with a permission error if the sender lacks it.
- Server ids must match exactly (case-sensitive); many commands accept short ids via tab completion.
- Several server commands accept no id when run by a player on an SLS server (they target the current server). Console usually requires an explicit id.
- Tab completion is available for commands.
