package tomatopotato.mochaccino.client;

import net.minecraft.client.Minecraft;

import org.lwjgl.sdl.SDLVideo;

import tomatopotato.mochaccino.Mochaccino;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

// shows the "restart to apply changes" notification; clicking it brings the game to the front
public final class RestartNotifier {
	private static Process helper;

	private RestartNotifier() {
	}

	public static void show(Minecraft client) {
		if (helper != null && helper.isAlive()) {
			return;
		}

		String title = SDLVideo.SDL_GetWindowTitle(client.getWindow().handle());
		String name = title == null || title.isEmpty() ? "Minecraft" : title;
		byte[] icon = MacMenuName.currentIconPng();

		Thread thread = new Thread(() -> post(client, name, icon), "mochaccino-notifier");
		thread.setDaemon(true);
		thread.start();
	}

	private static void post(Minecraft client, String name, byte[] icon) {
		try {
			Path executable = NotifierApp.ensure(name, icon);
			helper = new ProcessBuilder(
				executable.toString(),
				"--pid", String.valueOf(ProcessHandle.current().pid()),
				"--title", "Restart to apply changes",
				"--body", "Mod changes take effect the next time the game starts."
			).start();

			try (BufferedReader out = new BufferedReader(new InputStreamReader(helper.getInputStream(), StandardCharsets.UTF_8))) {
				if ("focus".equals(out.readLine())) {
					client.execute(MacMenuName::activateApp);
				}
			}

			int exit = helper.waitFor();
			if (exit == 2) {
				Mochaccino.LOGGER.warn("Notifications aren't allowed for this app; allow them in System Settings > Notifications");
			} else if (exit != 0) {
				Mochaccino.LOGGER.warn("The notifier exited with code {}", exit);
			}
		} catch (Exception e) {
			Mochaccino.LOGGER.warn("Could not show the restart notification", e);
		}
	}
}
