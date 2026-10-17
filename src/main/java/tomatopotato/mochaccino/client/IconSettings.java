package tomatopotato.mochaccino.client;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;

import tomatopotato.mochaccino.Mochaccino;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

// the user's chosen Dock icon, saved in config/mochaccino.properties
public final class IconSettings {
	private static final String KEY = "icon";
	private static final String LIQUID_GLASS_KEY = "liquidGlass";
	private static final String BACKGROUND_KEY = "background";
	public static final int DEFAULT_BACKGROUND = 0xFFFFFF;

	private IconSettings() {
	}

	private static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("mochaccino.properties");
	}

	private static Properties load() {
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(configFile())) {
			properties.load(reader);
		} catch (IOException e) {
		}
		return properties;
	}

	private static void store(Properties properties) {
		try (Writer writer = Files.newBufferedWriter(configFile())) {
			properties.store(writer, "Mochaccino");
		} catch (IOException e) {
			Mochaccino.LOGGER.warn("Could not save the settings", e);
		}
	}

	public static boolean useLiquidGlass() {
		return Boolean.parseBoolean(load().getProperty(LIQUID_GLASS_KEY, "false"));
	}

	public static void setUseLiquidGlass(boolean value) {
		Properties properties = load();
		properties.setProperty(LIQUID_GLASS_KEY, String.valueOf(value));
		store(properties);
	}

	public static int backgroundColor() {
		try {
			return Integer.parseInt(load().getProperty(BACKGROUND_KEY, ""), 16) & 0xFFFFFF;
		} catch (NumberFormatException e) {
			return DEFAULT_BACKGROUND;
		}
	}

	public static void setBackgroundColor(int rgb) {
		Properties properties = load();
		properties.setProperty(BACKGROUND_KEY, String.format("%06X", rgb & 0xFFFFFF));
		store(properties);
	}

	// the saved custom icon, or null if the user hasn't picked one (or it no longer exists)
	public static Path customIcon() {
		String value = load().getProperty(KEY, "");
		if (value.isEmpty()) {
			return null;
		}
		Path path = Path.of(value);
		return Files.isRegularFile(path) ? path : null;
	}

	// pass null to clear the saved icon
	public static void saveCustomIcon(Path path) {
		Properties properties = load();
		properties.setProperty(KEY, path == null ? "" : path.toAbsolutePath().toString());
		store(properties);
	}

	public static void apply() {
		if (customIcon() != null || useLiquidGlass()) {
			applyNow(null);
		}
	}

	private static int applyGeneration;

	public static void applyNow(Runnable onDone) {
		int generation = ++applyGeneration;
		Path custom = customIcon();
		byte[] source = null;
		if (custom != null) {
			try {
				source = Files.readAllBytes(custom);
			} catch (IOException e) {
				Mochaccino.LOGGER.warn("Could not read {}, using the default icon", custom, e);
			}
		}

		if (!useLiquidGlass()) {
			if (source == null || !MacMenuName.setIcon(source)) {
				MacMenuName.restoreIcon();
			}
			if (onDone != null) {
				onDone.run();
			}
			return;
		}

		byte[] png = MacMenuName.toPng(source != null ? source : MacMenuName.defaultIconPng());
		if (png == null) {
			Mochaccino.LOGGER.warn("The icon is not a readable image, leaving it as it is");
			if (onDone != null) {
				onDone.run();
			}
			return;
		}

		Minecraft client = Minecraft.getInstance();
		int background = backgroundColor();
		Thread thread = new Thread(() -> {
			byte[] rounded;
			try {
				rounded = RoundedIcon.toIcns(RoundedIcon.compose(png, background));
			} catch (Exception e) {
				Mochaccino.LOGGER.warn("Could not make the rounded icon. Using the image as it is", e);
				rounded = null;
			}
			byte[] icon = rounded;
			client.execute(() -> {
				// a newer choice was made while this one was being drawn
				if (generation != applyGeneration) {
					return;
				}
				if (icon == null || !MacMenuName.setIcon(icon)) {
					MacMenuName.setIcon(png);
				}
				if (onDone != null) {
					onDone.run();
				}
			});
		}, "mochaccino-rounded-icon");
		thread.setDaemon(true);
		thread.start();
	}

	public static boolean applyAndSave(Path path, Runnable onDone) {
		try {
			if (MacMenuName.toPng(Files.readAllBytes(path)) == null) {
				return false;
			}
		} catch (IOException e) {
			Mochaccino.LOGGER.warn("Could not read {}", path, e);
			return false;
		}
		saveCustomIcon(path);
		applyNow(onDone);
		return true;
	}

	public static void reset(Runnable onDone) {
		saveCustomIcon(null);
		applyNow(onDone);
	}
}
