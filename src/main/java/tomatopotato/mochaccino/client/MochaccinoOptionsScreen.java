package tomatopotato.mochaccino.client;

import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

public class MochaccinoOptionsScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.literal("Mochaccino");

	public MochaccinoOptionsScreen(Screen lastScreen, Options options) {
		super(lastScreen, options, TITLE);
	}

	@Override
	protected void addOptions() {
	}
}
