package com.adasjusk.killc.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
public final class KillCVelocity {

	static final ChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("killc:kill");

	private final ProxyServer proxy;
	private final Logger logger;
	private final Path dataDirectory;
	private String secret = "";

	@Inject
	public KillCVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
		this.proxy = proxy;
		this.logger = logger;
		this.dataDirectory = dataDirectory;
	}

	@Subscribe
	public void onInit(ProxyInitializeEvent event) {
		loadConfig();
		proxy.getChannelRegistrar().register(CHANNEL);

		CommandManager commands = proxy.getCommandManager();
		commands.register(commands.metaBuilder("kill").plugin(this).build(), new KillCommand());
		commands.register(commands.metaBuilder("suicide").plugin(this).build(), new SuicideCommand());
		logger.info("KillC (Velocity) enabled.");
	}

	private void loadConfig() {
		Path file = dataDirectory.resolve("config.properties");
		Properties properties = new Properties();
		try {
			if (Files.exists(file)) {
				try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					properties.load(reader);
				}
				secret = properties.getProperty("secret", "").trim();
			} else {
				byte[] random = new byte[32];
				new SecureRandom().nextBytes(random);
				secret = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

				properties.setProperty("secret", secret);
				Files.createDirectories(dataDirectory);
				try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
					properties.store(writer, "Copy this secret into proxy.secret in every backend server's KillC config.yml.");
				}
				logger.info("Generated a KillC proxy secret in {}. Copy it into proxy.secret "
					+ "in every backend server's plugins/KillC/config.yml.", file);
			}
		} catch (IOException e) {
			logger.error("Could not read {}; cross-server kills will not work until this is fixed.", file, e);
			secret = "";
		}

		if (secret.isEmpty()) {
			logger.error("No KillC proxy secret is configured; backend servers will reject every kill request.");
		}
	}

	private boolean sendKill(Player target, CommandSource feedbackTo) {
		Optional<ServerConnection> connection = target.getCurrentServer();
		if (connection.isEmpty()) {
			feedbackTo.sendMessage(Component.text("⚠ Target is not connected to a backend server.", NamedTextColor.RED));
			return false;
		}
		if (secret.isEmpty()) {
			feedbackTo.sendMessage(Component.text("⚠ KillC is not configured; see the proxy console.", NamedTextColor.RED));
			return false;
		}

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeUTF(secret);
			out.writeUTF(target.getUsername());
		} catch (IOException e) {
			// Writing to a byte array cannot fail, but the checked exception has to go somewhere.
			logger.error("Could not encode kill message", e);
			return false;
		}
		return connection.get().sendPluginMessage(CHANNEL, bytes.toByteArray());
	}

	private final class KillCommand implements SimpleCommand {

		@Override
		public void execute(Invocation invocation) {
			CommandSource source = invocation.source();
			String[] args = invocation.arguments();

			if (args.length == 0) {
				if (source instanceof Player player) {
					if (sendKill(player, source)) {
						source.sendMessage(Component.text("☠ You have killed yourself!", NamedTextColor.RED));
					}
				} else {
					source.sendMessage(Component.text("⚠ Console must specify a player: /kill <player>", NamedTextColor.RED));
				}
				return;
			}

			String targetName = args[0];
			boolean self = source instanceof Player player && player.getUsername().equalsIgnoreCase(targetName);
			if (!self && !source.hasPermission("killc.others")) {
				source.sendMessage(Component.text("✘ You don't have permission to kill other players!", NamedTextColor.RED));
				return;
			}

			Optional<Player> target = proxy.getPlayer(targetName);
			if (target.isEmpty()) {
				source.sendMessage(Component.text("✘ Player '" + targetName + "' not found on the network!", NamedTextColor.RED));
				return;
			}

			Player player = target.get();
			if (!sendKill(player, source)) {
				return;
			}
			if (!self) {
				player.sendMessage(Component.text("🗡 You have been killed by " + sourceName(source) + "!", NamedTextColor.RED));
			}
			source.sendMessage(Component.text("🗡 You have killed " + player.getUsername() + "!", NamedTextColor.GREEN));
		}

		@Override
		public List<String> suggest(Invocation invocation) {
			String[] args = invocation.arguments();
			if (args.length > 1 || !invocation.source().hasPermission("killc.others")) {
				return List.of();
			}
			String partial = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
			return proxy.getAllPlayers().stream()
				.map(Player::getUsername)
				.filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
				.toList();
		}

		@Override
		public boolean hasPermission(Invocation invocation) {
			return invocation.source().hasPermission("killc.self");
		}
	}

	private final class SuicideCommand implements SimpleCommand {

		@Override
		public void execute(Invocation invocation) {
			CommandSource source = invocation.source();
			if (!(source instanceof Player player)) {
				source.sendMessage(Component.text("✘ Only players can use the suicide command!", NamedTextColor.RED));
				return;
			}
			if (sendKill(player, source)) {
				source.sendMessage(Component.text("☠ You have committed suicide!", NamedTextColor.RED));
			}
		}

		@Override
		public boolean hasPermission(Invocation invocation) {
			return invocation.source().hasPermission("killc.self");
		}
	}

	private static String sourceName(CommandSource source) {
		return source instanceof Player player ? player.getUsername() : "Console";
	}
}