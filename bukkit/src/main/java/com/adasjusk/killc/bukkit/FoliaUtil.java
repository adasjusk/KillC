package com.adasjusk.killc.bukkit;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Runs entity tasks on the correct region thread when on Folia, or directly on Spigot/Paper.
 * Folia's EntityScheduler is accessed via reflection so this same jar still compiles and
 * runs against plain Spigot (which has no Folia classes).
 */
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

	private FoliaUtil() {}

	static boolean isFolia() {
		return FOLIA;
	}

	/** Run a task that touches the given entity on the thread that owns it. */
	static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
		if (!FOLIA) {
			task.run();
			return;
		}
		try {
			Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
			Method run = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
			Consumer<Object> consumer = ignored -> task.run();
			run.invoke(scheduler, plugin, consumer, (Runnable) null);
		} catch (ReflectiveOperationException e) {
			// Fallback: best effort, run inline
			task.run();
		}
	}
}
