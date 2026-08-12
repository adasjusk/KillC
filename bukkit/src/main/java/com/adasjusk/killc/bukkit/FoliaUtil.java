package com.adasjusk.killc.bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.util.function.Consumer;


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
			task.run();
		}
	}
}