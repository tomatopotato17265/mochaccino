package tomatopotato.mochaccino.client;

import tomatopotato.mochaccino.Mochaccino;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class NotifierApp {
	private static final String RESOURCE = "/mochaccino/notifier/MochaccinoNotifier";
	private static final String BUNDLE_ID = "com.tomatopotato.mochaccino.notifier";
	private static final String LSREGISTER = "/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister";

	private NotifierApp() {
	}

	private static Path directory() {
		return Path.of(System.getProperty("user.home"), "Library", "Application Support", "Mochaccino");
	}

	public static Path ensure(String name, byte[] iconPng) throws IOException, InterruptedException, GeneralSecurityException {
		Path bundle = directory().resolve("Notifier.app");
		Path executable = bundle.resolve("Contents/MacOS/MochaccinoNotifier");
		Path stampFile = directory().resolve("notifier.stamp");

		byte[] binary;
		try (InputStream in = NotifierApp.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				throw new IOException("Missing " + RESOURCE);
			}
			binary = in.readAllBytes();
		}

		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		digest.update(name.getBytes(StandardCharsets.UTF_8));
		digest.update((byte) 0);
		digest.update(iconPng == null ? new byte[0] : iconPng);
		digest.update((byte) 0);
		digest.update(binary);
		String stamp = HexFormat.of().formatHex(digest.digest());

		if (Files.isExecutable(executable) && Files.exists(stampFile) && Files.readString(stampFile).equals(stamp)) {
			return executable;
		}

		deleteRecursively(bundle);
		Files.createDirectories(executable.getParent());
		Files.createDirectories(bundle.resolve("Contents/Resources"));
		Files.write(executable, binary);
		if (!executable.toFile().setExecutable(true)) {
			throw new IOException("Could not make " + executable + " executable");
		}
		Files.writeString(bundle.resolve("Contents/Info.plist"), infoPlist(name, iconPng != null), StandardCharsets.UTF_8);
		if (iconPng != null) {
			makeIcns(iconPng, bundle.resolve("Contents/Resources/AppIcon.icns"));
		}

		run("/usr/bin/codesign", "--force", "--sign", "-", bundle.toString());
		if (Files.isExecutable(Path.of(LSREGISTER))) {
			run(LSREGISTER, "-f", bundle.toString());
		}

		Files.writeString(stampFile, stamp);
		return executable;
	}

	private static String infoPlist(String name, boolean hasIcon) {
		String escaped = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return """
			<?xml version="1.0" encoding="UTF-8"?>
			<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
			<plist version="1.0">
			<dict>
				<key>CFBundleIdentifier</key><string>%s</string>
				<key>CFBundleName</key><string>%s</string>
				<key>CFBundleDisplayName</key><string>%s</string>
				<key>CFBundleExecutable</key><string>MochaccinoNotifier</string>
				<key>CFBundlePackageType</key><string>APPL</string>
				<key>CFBundleVersion</key><string>1</string>
				<key>CFBundleShortVersionString</key><string>1.0</string>
				%s
				<key>LSUIElement</key><true/>
			</dict>
			</plist>
			""".formatted(BUNDLE_ID, escaped, escaped, hasIcon ? "<key>CFBundleIconFile</key><string>AppIcon</string>" : "");
	}

	// .png -> .iconset (every size macOS wants) -> .icns
	static void makeIcns(byte[] png, Path icns) throws IOException, InterruptedException {
		Path work = Files.createTempDirectory("mochaccino-icon");
		try {
			Path source = work.resolve("source.png");
			Files.write(source, png);
			Path iconset = Files.createDirectory(work.resolve("AppIcon.iconset"));

			int[] sizes = {16, 32, 128, 256, 512};
			for (int size : sizes) {
				resize(source, iconset.resolve("icon_" + size + "x" + size + ".png"), size);
				resize(source, iconset.resolve("icon_" + size + "x" + size + "@2x.png"), size * 2);
			}
			run("/usr/bin/iconutil", "-c", "icns", "-o", icns.toString(), iconset.toString());
		} finally {
			deleteRecursively(work);
		}
	}

	private static void resize(Path source, Path target, int size) throws IOException, InterruptedException {
		run("/usr/bin/sips", "-z", String.valueOf(size), String.valueOf(size), source.toString(), "--out", target.toString());
	}

	private static void run(String... command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(List.of(command)).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
			process.destroyForcibly();
			throw new IOException(command[0] + " failed: " + output.trim());
		}
	}

	private static void deleteRecursively(Path path) {
		if (!Files.exists(path)) {
			return;
		}
		try (var walk = Files.walk(path)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(entry -> {
				try {
					Files.delete(entry);
				} catch (IOException e) {
					Mochaccino.LOGGER.warn("Could not delete {}", entry, e);
				}
			});
		} catch (IOException e) {
			Mochaccino.LOGGER.warn("Could not delete {}", path, e);
		}
	}
}
