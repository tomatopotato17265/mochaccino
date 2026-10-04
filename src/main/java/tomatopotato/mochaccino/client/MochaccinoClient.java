package tomatopotato.mochaccino.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.OptionsScreen;

import org.lwjgl.sdl.SDLVideo;

import tomatopotato.mochaccino.Mochaccino;

import java.io.InputStream;

public class MochaccinoClient implements ClientModInitializer {
	private static volatile boolean clientReady = false;

	public static boolean isClientReady() {
		return clientReady;
	}

	// only start the mod if the user is on macOS and the client is ready
	@Override
	public void onInitializeClient() {
		if (!Mochaccino.isMacOS()) {
			return;
		}

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			clientReady = true;
			run(client);
		});
	}

	private static void run(Minecraft client) {
		try {
			String windowTitle = SDLVideo.SDL_GetWindowTitle(client.getWindow().handle());
			if (windowTitle != null && !windowTitle.isEmpty()) {
				MacMenuName.rename(windowTitle);
			}
			try (InputStream icon = MochaccinoClient.class.getResourceAsStream("/assets/mochaccino/icon.png")) {
				if (icon != null) {
					MacMenuName.setIcon(icon.readAllBytes());
				}
			}
			MacMenuName.addStandardMenus();
			MacMenuName.keepRenderingWhileTracking(() -> {
				try {
					client.renderFrame(true);
				} catch (Throwable t) {
					Mochaccino.LOGGER.warn("Could not render a frame during menu tracking", t);
				}
			});
			MacMenuName.addSettingsItem(() -> client.execute(() ->
				client.setScreenAndShow(new OptionsScreen(client.gui.screen(), client.options))));
		} catch (Throwable t) {
			Mochaccino.LOGGER.warn("Could not set up the macOS menu bar", t);
		}
	}
}
