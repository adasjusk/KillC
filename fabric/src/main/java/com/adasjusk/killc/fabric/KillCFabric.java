package com.adasjusk.killc.fabric;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public final class KillCFabric implements ModInitializer {
	private Config config = new Config();
	private final Random random = new Random();
	@Override
	public void onInitialize() {
		loadConfig();

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			dispatcher.register(Commands.literal("suicide").executes(ctx -> {
				ServerPlayer player = ctx.getSource().getPlayerOrException();
				player.setHealth(0.0f);
				player.sendSystemMessage(Component.literal("☠ You have committed suicide!"));
				return 1;
			}));
			//kill          - self
			//kill <player> - requires perm 2
			dispatcher.register(Commands.literal("killc")
				.executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					player.setHealth(0.0f);
					player.sendSystemMessage(Component.literal("☠ You have killed yourself!"));
					return 1;
				})
				.then(Commands.argument("target", EntityArgument.player())
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(ctx -> {
						ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
						target.setHealth(0.0f);
						target.sendSystemMessage(Component.literal("☠ You have been killed by " + sourceName(ctx.getSource()) + "!"));
						ctx.getSource().sendSuccess(() -> Component.literal("🗡 You have killed " + target.getName().getString() + "!"), false);
						return 1;
					})));
		});
		if (config.useRandomSpawn) {
			ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
				if (alive) return; // alive = true
				applyRandomSpawn(newPlayer);
			});
		}
	}
	private void applyRandomSpawn(ServerPlayer player) {
		ServerLevel level = player.level();
		int r = config.randomSpawnRadius;
		int x = random.nextInt(r * 2) - r;
		int z = random.nextInt(r * 2) - r;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
		player.teleportTo(x + 0.5, y, z + 0.5);
	}
	private static String sourceName(CommandSourceStack source) {
		return source.getTextName();
	}
	private void loadConfig() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("killc.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path)) {
					Config loaded = gson.fromJson(reader, Config.class);
					if (loaded != null) config = loaded;
				}
			} else {
				try (Writer writer = Files.newBufferedWriter(path)) {
					gson.toJson(config, writer);
				}
			}
		} catch (IOException e) {
		}
	}

	private static final class Config {
		boolean enabled = true;
		boolean useRandomSpawn = true;
		int randomSpawnRadius = 200;
	}
}