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

- `/kill` - Kill yourself (no arguments) or another player
- `/kill <player>` - Kill a specific player (requires permission)
- `/kill reload` - Reload the plugin configuration (ops only)
- `/suicide` - Kill yourself (alternative to `/kill` with no arguments)
- `/kill @e[type=entity]` - Kills entites like snowballs and any mob you would want! (ops only)

## 💎 Permissions

- `killc.self` - Basic permission to use kill commands (default: true)
- `killc.others` - Permission to kill other players (default: op)
- `killc.reload` - Permission to reload plugin config (default: op)
- `killc.*` - All permissions (default: op)

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

- **Minecraft Version**: 1.21.x
- **Server Software**: Paper, Folia
- **Java Version**: 21+
