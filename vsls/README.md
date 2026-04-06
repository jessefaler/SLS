# vSLS

vSLS is a [Velocity](https://velocitypowered.com/) plugin that lets you create and manage SLS servers through in-game commands. It also implements a simple matchmaking system and manages the lifecycle of servers.

## Requirements

- **Java 17+**
- A running **SLS** stack (Protocube + daemon) and a valid API URL and key in `config.yml`
- **Velocity 3.x** with **PacketEvents** installed; **ViaVersion** on the proxy is optional (used for protocol registration)

## Build

From this directory:

```bash
mvn -q package
```

The shaded plugin JAR is produced under `target/`.

## Configuration

Copy or edit `src/main/resources/config.yml` after the first run (the plugin writes defaults to the Velocity plugins folder). Set your API endpoint and credentials to match Protocube.

## Blueprint annotations

vSLS reads optional settings from the blueprint’s top-level `annotations` map (the same field described in the main SLS blueprint docs). Everything lives under `annotations.vsls`.

### `on-join`

A list of commands to run on the **server** whenever a player connects.

The placeholder `{PLAYER_NAME}` is replaced with the joining player’s username 

### `matchmaking`

Optional block used when registering this blueprint for matchmaking:

- **`maxPlayers`** — Maximum players per provisioned instance. For example, `1` starts a separate instance per player so each gets their own world/progress. Must be greater than zero for this block to apply; if you omit the entire `matchmaking` section, vSLS uses defaults (game type falls back to the blueprint id and a high default capacity).

### `dont-stop-when-empty`

If set to `true`, vSLS will **not** stop the server when it becomes empty (lifecycle manager). Defaults to `false`.

### `max-instances`

Controls how many instances of a single blueprint may exist at a time. If set, matchmaking will not create more than this many servers for that blueprint. Defaults to unlimited.

### Example

```yaml
annotations:
  vsls:
    dont-stop-when-empty: true
    max-instances: 1
    on-join:
      - run: 'say hello {PLAYER_NAME}'
      - run: 'playsound minecraft:block.note_block.bell ambient {PLAYER_NAME} ~ ~ ~ 1000 0'
    matchmaking:
      maxPlayers: 1
```

## Command reference

In-game and console commands are documented here:

**[vSLS command documentation](command_docs.md)**

Most admin operations use the `/sls` command tree and require `sls.command.admin` where noted in that document.

## License

See [LICENSE](LICENSE) in this directory.
