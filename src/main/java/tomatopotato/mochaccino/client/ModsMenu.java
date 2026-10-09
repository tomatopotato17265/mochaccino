package tomatopotato.mochaccino.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

import tomatopotato.mochaccino.Mochaccino;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ModsMenu {
	private static final String DISABLED_SUFFIX = ".disabled";


	private ModsMenu() {
	}

	private static final class Entry {
		final String name;
		// the mod's jar in the mods folder
		Path jar;
		boolean enabled;
		long item;

		Entry(String name, Path jar, boolean enabled) {
			this.name = name;
			this.jar = jar;
			this.enabled = enabled;
		}
	}

	public static void install() {
		MacMenuName.addSeparator("File");
		long modsMenu = MacMenuName.addFileSubmenu("Mods");
		if (modsMenu == 0L) {
			return;
		}

		for (Entry entry : entries()) {
			entry.item = MacMenuName.addCheckItem(modsMenu, entry.name, entry.enabled, entry.jar == null ? null : () -> toggle(entry));
		}
	}

	private static List<Entry> entries() {
		Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
		List<Entry> entries = new ArrayList<>();

		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			ModMetadata metadata = mod.getMetadata();
			String id = metadata.getId();
			// filter Minecraft, Java, the loader, libraries bundled inside other mods, and the Fabric API's bajillion modules
			if ("builtin".equals(metadata.getType())
				|| id.equals("fabricloader")
				|| id.equals("mixinextras")
				|| mod.getContainingMod().isPresent()
				|| (id.startsWith("fabric-") && !id.equals("fabric-api"))) {
				continue;
			}

			// only a jar sitting directly in the mods folder can be switched off by renaming it, and not this mod
			Path jar = null;
			if (mod.getOrigin().getKind() == ModOrigin.Kind.PATH && !id.equals(Mochaccino.MOD_ID)) {
				for (Path path : mod.getOrigin().getPaths()) {
					Path absolute = path.toAbsolutePath().normalize();
					if (Files.isRegularFile(absolute) && modsDir.equals(absolute.getParent())) {
						jar = absolute;
					}
				}
			}
			entries.add(new Entry(metadata.getName(), jar, true));
		}

		if (Files.isDirectory(modsDir)) {
			try (Stream<Path> files = Files.list(modsDir)) {
				files.filter(path -> path.getFileName().toString().endsWith(".jar" + DISABLED_SUFFIX))
					.forEach(path -> entries.add(new Entry(readName(path), path, false)));
			} catch (IOException e) {
				Mochaccino.LOGGER.warn("Could not look for disabled mods", e);
			}
		}

		entries.sort(Comparator.comparing(entry -> entry.name.toLowerCase()));
		return entries;
	}

	private static String readName(Path jar) {
		String fileName = jar.getFileName().toString();
		String fallback = fileName.substring(0, fileName.length() - ".jar".length() - DISABLED_SUFFIX.length());
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry metadata = zip.getEntry("fabric.mod.json");
			if (metadata == null) {
				return fallback;
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(zip.getInputStream(metadata), StandardCharsets.UTF_8)).getAsJsonObject();
			return json.has("name") ? json.get("name").getAsString() : fallback;
		} catch (Exception e) {
			return fallback;
		}
	}

	private static void toggle(Entry entry) {
		String name = entry.jar.getFileName().toString();
		String newName = entry.enabled ? name + DISABLED_SUFFIX : name.substring(0, name.length() - DISABLED_SUFFIX.length());
		Path target = entry.jar.resolveSibling(newName);
		try {
			Files.move(entry.jar, target);
		} catch (IOException e) {
			Mochaccino.LOGGER.warn("Could not {} {}", entry.enabled ? "disable" : "enable", entry.name, e);
			return;
		}

		entry.jar = target;
		entry.enabled = !entry.enabled;
		MacMenuName.setChecked(entry.item, entry.enabled);
	}
}
