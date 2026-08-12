package com.adasjusk.killc.velocity;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import java.util.List;
import java.util.Optional;

@Plugin(
	id = "killc",
	name = "KillC",
	version = "5.1",
	authors = {"adasjusk"},
	description = "Kill commands across a Velocity network."
)
public final class KillCVelocity {
	static final ChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("killc:kill");
	private final ProxyServer proxy;
	private final Logger logger;
	@Inject
	public KillCVelocity(ProxyServer proxy, Logger logger) {
		this.proxy = proxy;
		this.logger = logger;
	}

	@Subscribe
	public void onInit(ProxyInitializeEvent event) {
		proxy.getChannelRegistrar().register(CHANNEL);

		CommandManager cm = proxy.getCommandManager();
		cm.register(cm.metaBuilder("kill").plugin(this).build(), new KillCommand());
		cm.register(cm.metaBuilder("suicide").plugin(this).build(), new SuicideCommand());
		logger.info("KillC (Velocity) enabled.");
	}

	private void sendKill(Player onServerOf, String targetName, CommandSource feedbackTo) {
		Optional<ServerConnection> conn = onServerOf.getCurrentServer();
		if (conn.isEmpty()) {
			feedbackTo.sendMessage(Component.text("⚠ Target is not connected to a backend server.").color(NamedTextColor.RED));
			return;
		}
		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		out.writeUTF(targetName);
		conn.get().sendPluginMessage(CHANNEL, out.toByteArray());
	}
	private final class KillCommand implements SimpleCommand {
		@Override
		public void execute(Invocation invocation) {
			CommandSource source = invocation.source();
			String[] args = invocation.arguments();
			if (args.length == 0) {
				if (source instanceof Player) {
					Player p = (Player) source;
					sendKill(p, p.getUsername(), source);
					source.sendMessage(Component.text("☠ You have killed yourself!").color(NamedTextColor.RED));
				} else {
					source.sendMessage(Component.text("⚠ Console must specify a player: /kill <player>").color(NamedTextColor.RED));
				}
				return;
			}

			String targetName = args[0];
			boolean self = source instanceof Player && ((Player) source).getUsername().equalsIgnoreCase(targetName);
			if (!self && !source.hasPermission("killc.others")) {
				source.sendMessage(Component.text("✘ You don't have permission to kill other players!").color(NamedTextColor.RED));
				return;
			}
			Optional<Player> target = proxy.getPlayer(targetName);
			if (target.isEmpty()) {
				source.sendMessage(Component.text("✘ Player '" + targetName + "' not found on the network!").color(NamedTextColor.RED));
				return;
			}
			sendKill(target.get(), target.get().getUsername(), source);
			target.get().sendMessage(Component.text("🗡 You have been killed by " + sourceName(source) + "!").color(NamedTextColor.RED));
			source.sendMessage(Component.text("🗡 You have killed " + target.get().getUsername() + "!").color(NamedTextColor.GREEN));
		}

		@Override
		public List<String> suggest(Invocation invocation) {
			String[] args = invocation.arguments();
			if (args.length <= 1 && invocation.source().hasPermission("killc.others")) {
				String partial = args.length == 0 ? "" : args[0].toLowerCase();
				return proxy.getAllPlayers().stream()
					.map(Player::getUsername)
					.filter(n -> n.toLowerCase().startsWith(partial))
					.toList();
			}
			return List.of();
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
			if (!(source instanceof Player)) {
				source.sendMessage(Component.text("✘ Only players can use the suicide command!").color(NamedTextColor.RED));
				return;
			}
			Player p = (Player) source;
			sendKill(p, p.getUsername(), source);
			source.sendMessage(Component.text("☠ You have committed suicide!").color(NamedTextColor.RED));
		}

		@Override
		public boolean hasPermission(Invocation invocation) {
			return invocation.source().hasPermission("killc.self");
		}
	}
	private static String sourceName(CommandSource source) {
		return source instanceof Player ? ((Player) source).getUsername() : "Console";
	}
}