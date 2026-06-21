package com.adasjusk.killc.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class KillCBukkit extends JavaPlugin implements Listener, PluginMessageListener {

	// Channel used by the Velocity side of this same project to request a kill on a backend server.
	private static final String PROXY_CHANNEL = "killc:kill";

	private boolean pluginEnabled = true;
	private boolean useRandomSpawn = true;
	private int randomSpawnRadius = 200;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		loadConfig();
		getLogger().info("KillC enabled (" + (FoliaUtil.isFolia() ? "Folia" : "Spigot/Paper") + " mode)");

		KillCommandHandler handler = new KillCommandHandler(this);
		getServer().getPluginManager().registerEvents(this, this);

		getCommand("kill").setExecutor(handler);
		getCommand("kill").setTabCompleter(handler);
		getCommand("suicide").setExecutor(handler);
		getCommand("suicide").setTabCompleter(handler);

		// Listen for kill requests coming from the Velocity proxy module.
		getServer().getMessenger().registerIncomingPluginChannel(this, PROXY_CHANNEL, this);
	}

	@Override
	public void onDisable() {
		getLogger().info("KillC disabled.");
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

	public void reloadPluginConfig() {
		loadConfig();
	}

	/** Kill an entity safely on its owning region thread (Folia) or directly (Spigot/Paper). */
	void killEntity(Entity entity) {
		FoliaUtil.runForEntity(this, entity, () -> {
			if (entity instanceof Damageable) {
				((Damageable) entity).setHealth(0.0);
			}
		});
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		if (!useRandomSpawn) return;
		if (event.isBedSpawn() || event.isAnchorSpawn()) return;

		Player player = event.getPlayer();
		World world = player.getWorld();
		Random random = new Random();
		int x = random.nextInt(randomSpawnRadius * 2) - randomSpawnRadius;
		int z = random.nextInt(randomSpawnRadius * 2) - randomSpawnRadius;
		Location loc = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
		event.setRespawnLocation(loc);
	}

	// Incoming plugin message from the Velocity proxy: payload is the target player name.
	@Override
	public void onPluginMessageReceived(String channel, Player ignored, byte[] message) {
		if (!PROXY_CHANNEL.equals(channel)) return;
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
			String targetName = in.readUTF();
			Player target = Bukkit.getPlayerExact(targetName);
			if (target != null) {
				killEntity(target);
			}
		} catch (Exception e) {
			getLogger().warning("Bad proxy kill message: " + e.getMessage());
		}
	}

	private static final class KillCommandHandler implements CommandExecutor, TabCompleter {
		private final KillCBukkit plugin;

		KillCommandHandler(KillCBukkit plugin) {
			this.plugin = plugin;
		}

		private static void msg(CommandSender to, ChatColor color, String text) {
			to.sendMessage(color + text);
		}

		@Override
		public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
			String name = command.getName().toLowerCase();

			if (name.equals("kill") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
				if (!sender.hasPermission("killc.reload") && !sender.isOp()) {
					msg(sender, ChatColor.RED, "You don't have permission to reload this plugin!");
					return true;
				}
				plugin.reloadPluginConfig();
				msg(sender, ChatColor.GREEN, "KillC reloaded!");
				return true;
			}

			if (!plugin.isPluginEnabled()) {
				msg(sender, ChatColor.RED, "KillC is currently disabled!");
				return true;
			}

			if (name.equals("suicide")) {
				if (!(sender instanceof Player)) {
					msg(sender, ChatColor.RED, "Only players can use the suicide command!");
					return true;
				}
				Player player = (Player) sender;
				if (!player.hasPermission("killc.self") && !player.isOp()) {
					msg(player, ChatColor.RED, "You don't have permission to kill yourself!");
					return true;
				}
				plugin.killEntity(player);
				msg(player, ChatColor.RED, "You have committed suicide!");
				return true;
			}

			if (name.equals("kill")) {
				if (args.length == 0) {
					if (sender instanceof Player) {
						Player player = (Player) sender;
						if (!player.hasPermission("killc.self") && !player.isOp()) {
							msg(player, ChatColor.RED, "You don't have permission to kill yourself!");
							return true;
						}
						plugin.killEntity(player);
						msg(player, ChatColor.RED, "You have killed yourself!");
						return true;
					}
					msg(sender, ChatColor.RED, "Only players can kill themselves! Use /kill <player|selector>.");
					return true;
				}

				String targetArg = args[0];

				if (targetArg.startsWith("@")) {
					if (!sender.hasPermission("killc.selector") && !sender.isOp()) {
						msg(sender, ChatColor.RED, "You don't have permission to use selectors!");
						return true;
					}
					List<Entity> selected;
					try {
						selected = Bukkit.selectEntities(sender, targetArg);
					} catch (IllegalArgumentException ex) {
						msg(sender, ChatColor.RED, "Invalid selector: " + ex.getMessage());
						return true;
					}
					if (selected.isEmpty()) {
						msg(sender, ChatColor.RED, "No entities matched the selector.");
						return true;
					}
					int killedEntities = 0;
					int killedPlayers = 0;
					for (Entity e : selected) {
						if (e instanceof Player) {
							Player p = (Player) e;
							plugin.killEntity(p);
							msg(p, ChatColor.RED, "You have been killed by " + sender.getName() + "!");
							killedPlayers++;
							killedEntities++;
						} else if (e instanceof Damageable) {
							plugin.killEntity(e);
							killedEntities++;
						}
					}
					msg(sender, ChatColor.GREEN, "Killed " + killedEntities + " entities (" + killedPlayers + " players).");
					return true;
				}

				Player target = Bukkit.getPlayerExact(targetArg);
				if (target == null) {
					msg(sender, ChatColor.RED, "Player '" + targetArg + "' not found!");
					return true;
				}

				boolean isSelf = sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId());
				if (!sender.hasPermission("killc.others") && !sender.isOp() && !isSelf) {
					msg(sender, ChatColor.RED, "You don't have permission to kill other players!");
					return true;
				}
				plugin.killEntity(target);
				msg(target, ChatColor.RED, "You have been killed by " + sender.getName() + "!");
				msg(sender, ChatColor.GREEN, "You have killed " + target.getName() + "!");
				return true;
			}
			return false;
		}

		@Override
		public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
			List<String> completions = new ArrayList<>();
			if (command.getName().equalsIgnoreCase("kill") && args.length == 1) {
				String partial = args[0].toLowerCase();
				if ((sender.hasPermission("killc.reload") || sender.isOp()) && "reload".startsWith(partial)) {
					completions.add("reload");
				}
				if (sender.isOp() && "@e".startsWith(partial)) {
					completions.add("@e[type=]");
				}
				if (sender.hasPermission("killc.others") || sender.isOp()) {
					for (Player player : Bukkit.getOnlinePlayers()) {
						if (player.getName().toLowerCase().startsWith(partial)) {
							completions.add(player.getName());
						}
					}
				}
			}
			return completions;
		}
	}
}
