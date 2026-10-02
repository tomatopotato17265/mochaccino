package tomatopotato.mochaccino.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import tomatopotato.mochaccino.Mochaccino;

public class MochaccinoClient implements ClientModInitializer {
	private static volatile boolean clientReady = false;

	public static boolean isClientReady() {
		return clientReady;
	}

	@Override
	public void onInitializeClient() {
		if (!Mochaccino.isMacOS()) {
			return;
		}

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			clientReady = true;
		});
	}

	}
}