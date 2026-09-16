# KillC Official Plugin
> [!WARNING]
> This is the official page for this plugin; any other fork/plugin that uses kill is not managed by me.<br>
> This plugin is safe to use, because any other fork/plugin that uses /kill could have exploits!!!

Kill commands with configurable options for **Paper / Folia**, **Velocity** and **Fabric** servers.

## Downloads

Each release ships two files. Install the one that matches your server:

| File                         | Put it in  | Works on                                                           |
|------------------------------|------------|--------------------------------------------------------------------|
| `KillC-<version>.jar`        | `plugins/` | Paper, Folia, Purpur and **Velocity** (the same jar works on both) |
| `KillC-fabric-<version>.jar` | `mods/`    | Fabric (needs Fabric API)                                          |

## Features
- **Folia compatible** - runs on the entity's own region thread, no reflection hacks
- **Configurable** - enable/disable, random respawn, radius, proxy support
- **Permission-based** - proper permission system with op defaults
- **Random respawn** - respawn at a random surface location within a radius of (0, 0); beds and respawn anchors are always honoured
- **Tab completion** - player names, selectors and `reload`
- **Velocity network kills** - `/kill <player>` on the proxy kills a player on whichever backend server they are on

## Commands

### Paper / Folia
- `/kill` - Kill yourself.
- `/kill <player>` - Kill a specific player (requires `killc.others`).
- `/kill @e[type=...]` - Kill entities matched by a selector, including non-living ones like snowballs (requires `killc.selector`).
- `/kill reload` - Reload the plugin configuration (requires `killc.reload`).
- `/suicide` - Kill yourself.

### Velocity
- `/kill` - Kill yourself.
- `/kill <player>` - Kill any player on the network (requires `killc.others`).
- `/suicide` - Kill yourself.

### Fabric
- `/killc` - Kill yourself. (Vanilla owns `/kill`, so the mod does not override it.)
- `/killc <player>` - Kill a player (permission level 2).
- `/suicide` - Kill yourself.

## Permissions

- `killc.self` - Allows using basic kill commands on yourself (default: true).
- `killc.others` - Allows killing other players (default: op).
- `killc.selector` - Allows using selectors like `@e[type=...]` (default: op).
- `killc.reload` - Allows reloading the plugin configuration (default: op).
- `killc.*` - Gives access to all KillC commands (default: op).

## Configuration

### Paper / Folia - `plugins/KillC/config.yml`

```yaml
enabled: true
use-random-spawning-after-death: true
random-spawn-radius: 200

proxy:
  # Turn on only if KillC also runs on your Velocity proxy.
  enabled: false
  secret: "change-me"
```

### Velocity - `plugins/killc/config.properties`

Generated with a random `secret` the first time the proxy starts. Copy that
value into `proxy.secret` on every backend server and set `proxy.enabled: true`
there. Backend servers drop any kill request that does not carry the right
secret, so a modded client cannot forge one.

### Fabric - `config/killc.json`

```json
{
  "enabled": true,
  "useRandomSpawn": true,
  "randomSpawnRadius": 200
}
```

## Compatibility

- **Minecraft**: 26.2 (see `minecraft_version` / `api_version` in `gradle.properties`)
- **Server software**: Paper, Folia, Purpur, Velocity 4.x, Fabric
- **Java**: 25+