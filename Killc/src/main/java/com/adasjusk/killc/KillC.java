package com.adasjusk.killc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Damageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class KillC extends JavaPlugin implements Listener {

    private boolean pluginEnabled = true;
    private boolean useRandomSpawn = true;
    private int randomSpawnRadius = 200;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getLogger().info("KillC plugin has been enabled!");
        KillCommandHandler handler = new KillCommandHandler(this);
        getServer().getPluginManager().registerEvents(this, this);
        // Register commands
        this.getCommand("kill").setExecutor(handler);
        this.getCommand("kill").setTabCompleter(handler);
        this.getCommand("suicide").setExecutor(handler);
        this.getCommand("suicide").setTabCompleter(handler);
    }

    @Override
    public void onDisable() {
        getLogger().info("KillC plugin has been disabled!");
    }

    private void loadConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();
        pluginEnabled = config.getBoolean("enabled", true);
        useRandomSpawn = config.getBoolean("use-random-spawning-after-death", true);
        randomSpawnRadius = config.getInt("random-spawn-radius", 200);
    }

    public boolean isPluginEnabled() {
        return pluginEnabled;
    }

    public boolean isUseRandomSpawn() {
        return useRandomSpawn;
    }

    public int getRandomSpawnRadius() {
        return randomSpawnRadius;
    }

    public void reloadPluginConfig() {
        loadConfig();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!useRandomSpawn) return;
        Player player = event.getPlayer();
        World world = player.getWorld();
        Random random = new Random();
        int x = random.nextInt(randomSpawnRadius * 2) - randomSpawnRadius;
        int z = random.nextInt(randomSpawnRadius * 2) - randomSpawnRadius;
        Location loc = new Location(world, x, world.getHighestBlockYAt(x, z) + 1, z);
        event.setRespawnLocation(loc);
    }

    private static class KillCommandHandler implements CommandExecutor, TabCompleter {
        private final KillC plugin;

        public KillCommandHandler(KillC plugin) {
            this.plugin = plugin;
        }
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            String commandName = command.getName().toLowerCase();
            if (commandName.equals("kill") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("killc.reload") && !sender.isOp()) {
                    sender.sendMessage(Component.text("You don't have permission to reload this plugin!").color(NamedTextColor.RED));
                    return true;
                }
                plugin.reloadPluginConfig();
                sender.sendMessage(Component.text("KillC plugin reloaded!").color(NamedTextColor.GREEN));
                return true;
            }
            if (!plugin.isPluginEnabled()) {
                sender.sendMessage(Component.text("KillC plugin is currently disabled!").color(NamedTextColor.RED));
                return true;
            }
            if (commandName.equals("suicide")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Component.text("Only players can use the suicide command!").color(NamedTextColor.RED));
                    return true;
                }
                Player player = (Player) sender;
                if (!player.hasPermission("killc.self") && !player.isOp()) {
                    player.sendMessage(Component.text("You don't have permission to kill yourself!").color(NamedTextColor.RED));
                    return true;
                }
                player.setHealth(0.0);
                player.sendMessage(Component.text("You have committed suicide!").color(NamedTextColor.RED));
                return true;
            }

            // i plan for this implementation but idk if it's good idea for dos
            // if (selected.size() > 100) {
            //     sender.sendMessage(Component.text("Too many targets!").color(NamedTextColor.RED));
            //     return true;
            // }


            if (commandName.equals("kill")) {
                if (args.length == 0) {
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        if (!player.hasPermission("killc.self") && !player.isOp()) {
                            player.sendMessage(Component.text("You don't have permission to kill yourself!").color(NamedTextColor.RED));
                            return true;
                        }
                        player.setHealth(0.0);
                        player.sendMessage(Component.text("You have killed yourself!").color(NamedTextColor.RED));
                        return true;
                    } else {
                        sender.sendMessage(Component.text("Only players can kill themselves! Use /kill <player|selector> to kill a specific target.").color(NamedTextColor.RED));
                        return true;
                    }
                }

                String targetArg = args[0];
                boolean looksLikeSelector = targetArg.startsWith("@");

                if (looksLikeSelector) {
                    if (!sender.isOp()) {
                        sender.sendMessage(Component.text("Only operators can use selectors like @e[type=...]!").color(NamedTextColor.RED));
                        return true;
                    }
                    List<Entity> selected;
                    try {
                        selected = Bukkit.selectEntities(sender, targetArg);
                    } catch (IllegalArgumentException ex) {
                        sender.sendMessage(Component.text("Invalid selector: " + ex.getMessage()).color(NamedTextColor.RED));
                        return true;
                    }
                    if (selected.isEmpty()) {
                        sender.sendMessage(Component.text("No entities matched the selector.").color(NamedTextColor.RED));
                        return true;
                    }
                    int killedEntities = 0;
                    int killedPlayers = 0;
                    for (Entity e : selected) {
                        if (e instanceof Player) {
                            Player p = (Player) e;
                            p.setHealth(0.0);
                            p.sendMessage(Component.text("You have been killed by " + sender.getName() + "!").color(NamedTextColor.RED));
                            killedPlayers++;
                            killedEntities++;
                        } else if (e instanceof Damageable) {
                            ((Damageable) e).setHealth(0.0);
                            killedEntities++;
                        }
                    }
                    sender.sendMessage(Component.text("Killed " + killedEntities + " entities (" + killedPlayers + " players).").color(NamedTextColor.GREEN));
                    return true;
                }

                Player targetPlayer = Bukkit.getPlayerExact(targetArg);
                if (targetPlayer == null) {
                    sender.sendMessage(Component.text("Player '" + targetArg + "' not found!").color(NamedTextColor.RED));
                    return true;
                }

                if (!sender.hasPermission("killc.others") && !sender.isOp() && !(sender instanceof Player && ((Player) sender).getUniqueId().equals(targetPlayer.getUniqueId()))) {
                    sender.sendMessage(Component.text("You don't have permission to kill other players!").color(NamedTextColor.RED));
                    return true;
                }
                targetPlayer.setHealth(0.0);
                targetPlayer.sendMessage(Component.text("You have been killed by " + sender.getName() + "!").color(NamedTextColor.RED));
                sender.sendMessage(Component.text("You have killed " + targetPlayer.getName() + "!").color(NamedTextColor.GREEN));
                return true;
            }

            return false;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> completions = new ArrayList<>();
            String commandName = command.getName().toLowerCase();
            if (commandName.equals("kill") && args.length == 1) {
                String partial = args[0].toLowerCase();
                if ((sender.hasPermission("killc.reload") || sender.isOp()) && "reload".startsWith(partial)) completions.add("reload");
                if (sender.isOp() && "@e".startsWith(partial)) completions.add("@e[type=]");
                if (sender.hasPermission("killc.others") || sender.isOp()) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player.getName().toLowerCase().startsWith(partial)) completions.add(player.getName());
                    }
                }
            }
            return completions;
        }
    }
}