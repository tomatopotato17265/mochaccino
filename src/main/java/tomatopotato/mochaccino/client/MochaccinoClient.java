package tomatopotato.mochaccino.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

import org.lwjgl.sdl.SDLVideo;

import tomatopotato.mochaccino.Mochaccino;

import java.util.function.Consumer;

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

	private static EditBox focusedEditBox(Minecraft client) {
		Screen screen = client.gui.screen();
		if (screen == null) {
			return null;
		}
		GuiEventListener focused = screen.getFocused();
		while (focused instanceof ContainerEventHandler container && container.getFocused() != null) {
			focused = container.getFocused();
		}
		return focused instanceof EditBox box && box.canConsumeInput() ? box : null;
	}

	private static void withEditBox(Minecraft client, Consumer<EditBox> action) {
		EditBox box = focusedEditBox(client);
		if (box != null) {
			action.accept(box);
		}
	}

	private static void run(Minecraft client) {
		try {
			String windowTitle = SDLVideo.SDL_GetWindowTitle(client.getWindow().handle());
			if (windowTitle != null && !windowTitle.isEmpty()) {
				MacMenuName.rename(windowTitle);
			}
			MacMenuName.removeServicesItem();
			IconSettings.apply();
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

			MacMenuName.addEditActions(
				() -> focusedEditBox(client) != null,
				() -> withEditBox(client, box -> {
					client.keyboardHandler.setClipboard(box.getHighlighted());
					box.insertText("");
				}),
				() -> withEditBox(client, box -> client.keyboardHandler.setClipboard(box.getHighlighted())),
				() -> withEditBox(client, box -> box.insertText(client.keyboardHandler.getClipboard())),
				() -> withEditBox(client, box -> {
					box.moveCursorToEnd(false);
					box.setHighlightPos(0);
				})
			);
			MacMenuName.addNewWorldItem(() -> client.execute(() -> {
				// a world can't be created while another one is open
				if (client.level == null) {
					Screen previous = client.gui.screen();
					CreateWorldScreen.openFresh(client, () -> client.setScreenAndShow(previous));
				}
			}));
			ModsMenu.install();
		} catch (Throwable t) {
			Mochaccino.LOGGER.warn("Could not set up the macOS menu bar", t);
		}
	}
}
