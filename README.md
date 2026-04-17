# KillC Official Plugin
> [!WARNING] 
> This is official page for this plugin, any other fork/plugin that uses kill is not managed by me<br>
> This plugin is safe to use, because any other fork/plugin that uses /kill could have exploits!!!

Minecraft plugin for Paper/Folia 1.21.10 that provides kill commands with configurable options.

## 🎇 Features
- **Single file implementation** - All functionality consolidated into one Java file
- **Folia compatible** - Fully supports Folia server software
- **Configurable** - Enable/disable plugin functionality via config
- **Multiple commands** - Separate commands for different use cases
- **Permission-based** - Proper permission system with op defaults
- **Random spawn usage** - You can set from X 0, Z 0 to a radius for spawning randomly
- **Tab completion** - Smart tab completion for player names and commands

## 🦺 Commands

- `/kill` - Kill yourself (no arguments).
- `/kill <player>` - Kill a specific player (requires `killc.others` permission).
- `/kill @e[type=entity]` - Kills entities like snowballs and any mob you would want! (ops only, requires `killc.selector`).
- `/kill reload` - Reload the plugin configuration (requires `killc.reload` permission).
- `/suicide` - Kill yourself (alternative to `/kill` with no arguments, requires `killc.self`).

## 💎 Permissions

- `killc.self` - Allows using basic kill commands on yourself (default: true).
- `killc.others` - Allows killing other players (default: op).
- `killc.selector` - Allows using selectors like @e[type=...] (default: op).
- `killc.reload` - Allows reloading the plugin configuration (default: op).
- `killc.*` - Gives access to all KillC commands (default: op).

## 🎨 Configuration

The plugin creates a `config.yml` file with the following options:

```yaml
# KillC Plugin Configuration
enabled: true
use-random-spawning-after-death: true
# Maximum random distance from (0,0) spawn for respawn
random-spawn-radius: 200
```

## 🔓 Compatibility

- **Minecraft Version**: 26.x
- **Server Software**: Paper, Folia
- **Java Version**: 25+
