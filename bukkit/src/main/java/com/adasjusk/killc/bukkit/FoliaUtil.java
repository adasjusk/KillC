package com.adasjusk.killc.bukkit;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

final class FoliaUtil {

	private static final boolean FOLIA;

	static {
		boolean folia;
		try {
			Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
			folia = true;
		} catch (ClassNotFoundException e) {
			folia = false;
		}
		FOLIA = folia;
	}

	private FoliaUtil() {
	}

	static boolean isFolia() {
		return FOLIA;
	}
	static void runForEntity(Plugin plugin, Entity entity, Runnable task, Runnable retired) {
		entity.getScheduler().run(plugin, ignored -> task.run(), retired);
	}
}