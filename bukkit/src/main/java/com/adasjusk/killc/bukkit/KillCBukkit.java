package com.adasjusk.killc.bukkit;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class KillCBukkit extends JavaPlugin implements Listener, PluginMessageListener {

	private static final String PROXY_CHANNEL = "killc:kill";
	private static final String DEFAULT_SECRET = "change-me";
	/** vanilla world border limit; a respawn past this is not a valid location. */
	private static final int MAX_SPAWN_RADIUS = 29_999_983;
	/** how many times to look for a random respawn spot that is not a liquid. */
	private static final int SPAWN_ATTEMPTS = 8;
	private boolean pluginEnabled = true;
	private boolean useRandomSpawn = true;
	private int randomSpawnRadius = 200;
	private boolean proxyEnabled;
	private byte[] proxySecret = new byte[0];
	private boolean channelRegistered;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		loadConfig();
		getServer().getPluginManager().registerEvents(this, this);
		registerCommand("kill", "Kills yourself, another player, or a selector. /kill reload reloads the config.", new KillCommand());
		registerCommand("suicide", "Kills yourself.", new SuicideCommand());
		getLogger().info("KillC enabled (" + (FoliaUtil.isFolia() ? "Folia" : "Paper") + " mode)");
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
		int radius = config.getInt("random-spawn-radius", 200);
		randomSpawnRadius = Math.clamp(radius, 1, MAX_SPAWN_RADIUS);
		if (randomSpawnRadius != radius) {
			getLogger().warning("random-spawn-radius " + radius + " is out of range; using " + randomSpawnRadius + ".");
		}

		proxyEnabled = config.getBoolean("proxy.enabled", false);
		String secret = config.getString("proxy.secret", "");
		proxySecret = secret.getBytes(StandardCharsets.UTF_8);

		if (proxyEnabled && (secret.isBlank() || DEFAULT_SECRET.equals(secret))) {
			proxyEnabled = false;
			getLogger().severe("proxy.enabled is true but proxy.secret is still the default. "
				+ "Cross-server kills are disabled until you set a real secret in config.yml "
				+ "and in the proxy's plugins/killc/config.properties.");
		}
		updateChannelRegistration();
	}

	private void updateChannelRegistration() {
		if (proxyEnabled && !channelRegistered) {
			getServer().getMessenger().registerIncomingPluginChannel(this, PROXY_CHANNEL, this);
			channelRegistered = true;
		} else if (!proxyEnabled && channelRegistered) {
			getServer().getMessenger().unregisterIncomingPluginChannel(this, PROXY_CHANNEL, this);
			channelRegistered = false;
		}
	}

	public boolean isPluginEnabled() {
		return pluginEnabled;
	}

	public void reloadPluginConfig() {
		loadConfig();
	}

	void killEntity(Entity entity) {
		FoliaUtil.runForEntity(this, entity, () -> {
			if (entity instanceof Damageable damageable) {
				damageable.kill();
			} else {
				entity.remove();
			}
		}, null);
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		if (!useRandomSpawn) {
			return;
		}
		if (event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) {
			return;
		}
		if (event.isBedSpawn() || event.isAnchorSpawn()) {
			return;
		}

		Location random = randomSpawn(event.getPlayer().getWorld());
		if (random != null) {
			event.setRespawnLocation(random);
		}
	}

	private Location randomSpawn(World world) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		try {
			for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
				int x = random.nextInt(-randomSpawnRadius, randomSpawnRadius + 1);
				int z = random.nextInt(-randomSpawnRadius, randomSpawnRadius + 1);
				int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
				Location location = new Location(world, x + 0.5, y + 1, z + 0.5);
				if (!world.getType(x, y, z).isSolid()) {
					continue; // water, lava or air below the player
				}
				return location;
			}
		} catch (RuntimeException e) {
			getLogger().warning("Could not pick a random respawn location: " + e);
			return null;
		}
		return null; // no solid ground found; fall back to the normal respawn point
	}

	@Override
	public void onPluginMessageReceived(@NotNull String channel, @NotNull Player source, byte @NotNull [] message) {
		if (!proxyEnabled || !PROXY_CHANNEL.equals(channel)) {
			return;
		}

		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
			byte[] secret = in.readUTF().getBytes(StandardCharsets.UTF_8);
			String targetName = in.readUTF();

			if (!MessageDigest.isEqual(secret, proxySecret)) {
				getLogger().warning("Rejected a kill request on " + PROXY_CHANNEL
					+ " with a bad secret (received via " + source.getName() + "). "
					+ "Either a client is spoofing proxy messages, or the proxy's secret does not match config.yml.");
				return;
			}

			Player target = Bukkit.getPlayerExact(targetName);
			if (target != null) {
				killEntity(target);
			}
		} catch (IOException e) {
			getLogger().warning("Malformed proxy kill message: " + e.getMessage());
		}
	}

	private static void msg(CommandSender to, NamedTextColor color, String text) {
		to.sendMessage(Component.text(text, color));
	}

	private boolean checkEnabled(CommandSender sender) {
		if (!pluginEnabled) {
			msg(sender, NamedTextColor.RED, "⚠ KillC is currently disabled!");
			return false;
		}
		return true;
	}

	private final class SuicideCommand implements BasicCommand {

		@Override
		public void execute(@NotNull CommandSourceStack source, String @NotNull [] args) {
			CommandSender sender = source.getSender();
			if (!checkEnabled(sender)) {
				return;
			}
			if (!(sender instanceof Player player)) {
				msg(sender, NamedTextColor.RED, "⚠ Only players can use the suicide command!");
				return;
			}
			killEntity(player);
			msg(player, NamedTextColor.RED, "☠ You have committed suicide!");
		}

		@Override
		public String permission() {
			return "killc.self";
		}
	}

	private final class KillCommand implements BasicCommand {

		@Override
		public void execute(@NotNull CommandSourceStack source, String @NotNull [] args) {
			CommandSender sender = source.getSender();
			if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
				if (!sender.hasPermission("killc.reload")) {
					msg(sender, NamedTextColor.RED, "You don't have permission to reload this plugin!");
					return;
				}
				reloadPluginConfig();
				msg(sender, NamedTextColor.GREEN, "KillC reloaded!");
				return;
			}

			if (!checkEnabled(sender)) {
				return;
			}

			if (args.length == 0) {
				if (!(sender instanceof Player player)) {
					msg(sender, NamedTextColor.RED, "❌ Only players can kill themselves! Use /kill <player|selector>.");
					return;
				}
				killEntity(player);
				msg(player, NamedTextColor.RED, "☠ You have killed yourself!");
				return;
			}

			String targetArg = args[0];
			if (targetArg.startsWith("@")) {
				killSelector(sender, targetArg);
				return;
			}

			Player target = Bukkit.getPlayerExact(targetArg);
			if (target == null) {
				msg(sender, NamedTextColor.RED, "♯ Player '" + targetArg + "' not found!");
				return;
			}

			boolean isSelf = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
			if (!isSelf && !sender.hasPermission("killc.others")) {
				msg(sender, NamedTextColor.RED, "❌ You don't have permission to kill other players!");
				return;
			}

			killEntity(target);
			msg(target, NamedTextColor.RED, "☠ You have been killed by " + sender.getName() + "!");
			msg(sender, NamedTextColor.GREEN, "🗡 You have killed " + target.getName() + "!");
		}

		private void killSelector(CommandSender sender, String selector) {
			if (!sender.hasPermission("killc.selector")) {
				msg(sender, NamedTextColor.RED, "❌ You don't have permission to use selectors!");
				return;
			}

			List<Entity> selected;
			try {
				selected = Bukkit.selectEntities(sender, selector);
			} catch (IllegalArgumentException e) {
				msg(sender, NamedTextColor.RED, "❌ Invalid selector: " + e.getMessage());
				return;
			}

			if (selected.isEmpty()) {
				msg(sender, NamedTextColor.RED, "🗡 No entities matched the selector.");
				return;
			}

			int killedPlayers = 0;
			for (Entity entity : selected) {
				killEntity(entity);
				if (entity instanceof Player player) {
					msg(player, NamedTextColor.RED, "🗡 You have been killed by " + sender.getName() + "!");
					killedPlayers++;
				}
			}
			msg(sender, NamedTextColor.GREEN,
				"\ud83e\ude93 Killed " + selected.size() + " entities (" + killedPlayers + " players).");
		}

		@Override
		public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, String @NotNull [] args) {
			if (args.length > 1) {
				return List.of();
			}
			CommandSender sender = source.getSender();
			String partial = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
			List<String> completions = new ArrayList<>();

			if (sender.hasPermission("killc.reload") && "reload".startsWith(partial)) {
				completions.add("reload");
			}
			if (sender.hasPermission("killc.selector") && "@e".startsWith(partial)) {
				completions.add("@e[type=]");
			}
			if (sender.hasPermission("killc.others")) {
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (player.getName().toLowerCase(Locale.ROOT).startsWith(partial)) {
						completions.add(player.getName());
					}
				}
			}
			return completions;
		}

		@Override
		public String permission() {
			return "killc.self";
}	}	}