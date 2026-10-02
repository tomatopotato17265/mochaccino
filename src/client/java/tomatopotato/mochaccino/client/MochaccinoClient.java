package tomatopotato.mochaccino.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import net.minecraft.client.Minecraft;

import org.lwjgl.sdl.SDLVideo;

import tomatopotato.mochaccino.Mochaccino;

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
		} catch (Throwable t) {
			Mochaccino.LOGGER.warn("Could not rename the macOS menu bar app name", t);
		}
	}
}