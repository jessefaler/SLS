# SlimePacks

SlimePacks is a **Protocube** plugin that hosts Minecraft Java resource packs and serves them over HTTP. It discovers packs from blueprint metadata, converts them to the Minecraft version clients ask for, and caches the results so repeat downloads stay fast.

## What it does

- Scans registered blueprints for a `resource_pack` annotation and registers each pack under the blueprint id.
- Reads the pack from your configured root (see `config.yaml`): each pack lives under `{root}/{blueprint-id}/pack/` with a normal `pack.mcmeta` layout.
- When a client requests a specific game version, SlimePacks maps that version to a pack format, runs conversion if the cached zip is missing, then serves a **zip** download.
- Exposes a small API under `/api/slimepacks`.

## How conversion works

SlimePacks shells out to **[ResourcePackConverter](https://github.com/agentdid127/ResourcePackConverter)** (Java) with `--from`, `--to`, and `--input` pointing at the pack tree. That project rewrites pack assets so they work on target Minecraft versions between roughly 1.7.2 and current releases.

On startup, the plugin can **auto-download** the latest `ResourcePackConverter.jar` from the [agentdid127/ResourcePackConverter](https://github.com/agentdid127/ResourcePackConverter) GitHub releases (configurable via `auto-update` in `config.yaml`). You need a **Java** runtime available as `java` on the server’s `PATH` for conversions to run.

Converted artifacts are stored under your configured `conversions-folder`, keyed by blueprint id and version range, so the same request does not re-run the converter every time.

## HTTP API

| Method | Path                  | Description                                                                                    |
|--------|-----------------------|------------------------------------------------------------------------------------------------|
| `GET`  | `/api/slimepacks`     | JSON list of registered pack ids (`slimepacks`).                                               |
| `GET`  | `/api/slimepacks/:id` | Zip download for that pack. Use query `version` or `v` with a Minecraft version (e.g. `1.20`). |

Example:

`https://example.com/api/slimepacks/combat_cube?version=1.20`

## Configuration

Config file: `{pluginsDir}/SlimePacks/config.yaml` (created with defaults on first run). Relevant fields include `root-folder` (resource packs root), `conversions-folder`, `converter-jar`, and `auto-update`.

## Building the plugin

SlimePacks is a Go **plugin** for Protocube (`package main` with a `Plugin` symbol). Build from this directory with the same Go toolchain version Protocube was built with (Go plugins require matching versions).

```bash
cd slimepacks
go build -buildmode=plugin -o SlimePacks.so
```

Install the resulting `.so` in your Protocube plugins directory

## License

See [LICENSE](LICENSE).
