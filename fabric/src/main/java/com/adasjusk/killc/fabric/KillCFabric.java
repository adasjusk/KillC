package com.adasjusk.killc.fabric;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

public final class KillCFabric implements ModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("KillC");
	/** Vanilla world border limit. */
	private static final int MAX_SPAWN_RADIUS = 29_999_983;
	private static final int SPAWN_ATTEMPTS = 8;
	private Config config = new Config();

	@Override
	public void onInitialize() {
		loadConfig();

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
			dispatcher.register(Commands.literal("suicide")
				.requires(source -> config.enabled)
				.executes(context -> {
					ServerPlayer player = context.getSource().getPlayerOrException();
					player.kill(player.level());
					player.sendSystemMessage(Component.literal("☠ You have committed suicide!"));
					return 1;
				}));
			dispatcher.register(Commands.literal("killc")
				.requires(source -> config.enabled)
				.executes(context -> {
					ServerPlayer player = context.getSource().getPlayerOrException();
					player.kill(player.level());
					player.sendSystemMessage(Component.literal("☠ You have killed yourself!"));
					return 1;
				})
				.then(Commands.argument("target", EntityArgument.player())
					.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.executes(context -> {
						ServerPlayer target = EntityArgument.getPlayer(context, "target");
						target.kill(target.level());
						target.sendSystemMessage(Component.literal("☠ You have been killed by " + context.getSource().getTextName() + "!"));
						context.getSource().sendSuccess(
							() -> Component.literal("🗡 You have killed " + target.getName().getString() + "!"), false);
						return 1;
					})));
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (alive || !config.enabled || !config.useRandomSpawn) {
				return;
			}
			applyRandomSpawn(newPlayer);
		});
	}

	private void applyRandomSpawn(ServerPlayer player) {
		ServerLevel level = player.level();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int radius = Math.clamp(config.randomSpawnRadius, 1, MAX_SPAWN_RADIUS);
		for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
			int x = random.nextInt(-radius, radius + 1);
			int z = random.nextInt(-radius, radius + 1);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockState ground = level.getBlockState(new BlockPos(x, y - 1, z));
			if (ground.isAir() || !ground.getFluidState().isEmpty()) {
				continue; // nothing to stand on, or water/lava
			}
			player.teleportTo(x + 0.5, y, z + 0.5);
			return;
		}
		LOGGER.warn("Could not find a random respawn spot within {} blocks; leaving the vanilla spawn point.", radius);
	}

	private void loadConfig() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("killc.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					Config loaded = gson.fromJson(reader, Config.class);
					if (loaded != null) {
						config = loaded;
					}
				}
			} else {
				try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
					gson.toJson(config, writer);
				}
			}
		} catch (IOException | JsonSyntaxException e) {
			LOGGER.warn("Could not read {}; falling back to default settings.", path, e);
			config = new Config();
		}
	}

	private static final class Config {
		boolean enabled = true;
		boolean useRandomSpawn = true;
		int randomSpawnRadius = 200;
	}
}