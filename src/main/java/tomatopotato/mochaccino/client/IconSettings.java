package tomatopotato.mochaccino.client;

import net.fabricmc.loader.api.FabricLoader;

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

	private IconSettings() {
	}

	private static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("mochaccino.properties");
	}

	// the saved custom icon, or null if the user hasn't picked one (or it no longer exists)
	public static Path customIcon() {
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(configFile())) {
			properties.load(reader);
		} catch (IOException e) {
			return null;
		}
		String value = properties.getProperty(KEY, "");
		if (value.isEmpty()) {
			return null;
		}
		Path path = Path.of(value);
		return Files.isRegularFile(path) ? path : null;
	}

	// pass null to clear the saved icon
	public static void saveCustomIcon(Path path) {
		Properties properties = new Properties();
		properties.setProperty(KEY, path == null ? "" : path.toAbsolutePath().toString());
		try (Writer writer = Files.newBufferedWriter(configFile())) {
			properties.store(writer, "Mochaccino");
		} catch (IOException e) {
			Mochaccino.LOGGER.warn("Could not save the icon setting", e);
		}
	}

	public static void apply() {
		Path custom = customIcon();
		if (custom == null) {
			return;
		}
		try {
			if (!MacMenuName.setIcon(Files.readAllBytes(custom))) {
				Mochaccino.LOGGER.warn("{} is not a readable image, leaving the default icon", custom);
			}
		} catch (IOException e) {
			Mochaccino.LOGGER.warn("Could not read {}, leaving the default icon", custom, e);
		}
	}

	// applies the given image if it's a valid one and remembers it
	public static boolean applyAndSave(Path path) {
		try {
			if (MacMenuName.setIcon(Files.readAllBytes(path))) {
				saveCustomIcon(path);
				return true;
			}
		} catch (IOException e) {
			Mochaccino.LOGGER.warn("Could not read {}", path, e);
		}
		return false;
	}

	// reset icon
	public static void reset() {
		saveCustomIcon(null);
		MacMenuName.restoreIcon();
	}
}
