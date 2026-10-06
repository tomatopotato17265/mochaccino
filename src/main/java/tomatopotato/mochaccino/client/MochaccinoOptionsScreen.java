package tomatopotato.mochaccino.client;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import tomatopotato.mochaccino.Mochaccino;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public class MochaccinoOptionsScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.literal("Mochaccino");

	private static final Identifier PREVIEW_ID = Mochaccino.id("icon_preview");
	private static final int PREVIEW_SIZE = 32;
	private static final int TEXTURE_SIZE = 256;
	private static final int MARGIN = 8;
	private static final int RESET_WIDTH = 60;
	private static final int MAX_NAME_WIDTH = 200;
	private static final int NAME_GAP = 3;

	private IconWidget iconWidget;
	private Button resetButton;
	private boolean hasPreview;
	private String iconName = "Default";

	public MochaccinoOptionsScreen(Screen lastScreen, Options options) {
		super(lastScreen, options, TITLE);
	}

	@Override
	protected void addOptions() {}

	@Override
	protected void init() {
		iconWidget = null;
		resetButton = null;
		super.init();

		iconWidget = addRenderableWidget(new IconWidget());
		resetButton = addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
			IconSettings.reset();
			refresh();
		}).width(RESET_WIDTH).build());

		iconWidget.active = Mochaccino.isMacOS();
		placeWidgets();
		refresh();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (iconWidget != null && iconWidget.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (resetButton != null && resetButton.mouseClicked(event, doubleClick)) {
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();
		placeWidgets();
	}

	private int textWidth() {
		return Math.max(font.width(fit(iconName, MAX_NAME_WIDTH)), RESET_WIDTH);
	}

	private int rowLeft() {
		return (width - (PREVIEW_SIZE + MARGIN + textWidth())) / 2;
	}

	private int rowTop() {
		return layout.getHeaderHeight() + MARGIN;
	}

	private void placeWidgets() {
		if (iconWidget == null || resetButton == null) {
			return;
		}
		iconWidget.setX(rowLeft());
		iconWidget.setY(rowTop());
		resetButton.setX(rowLeft() + PREVIEW_SIZE + MARGIN);
		resetButton.setY(rowTop() + font.lineHeight + NAME_GAP);
	}

	private void refresh() {
		Path custom = IconSettings.customIcon();
		iconName = custom == null ? "Default" : custom.getFileName().toString();
		resetButton.active = Mochaccino.isMacOS() && custom != null;
		refreshPreview();
		placeWidgets();
	}

	private void refreshPreview() {
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		textures.release(PREVIEW_ID);
		hasPreview = false;
		if (!Mochaccino.isMacOS()) {
			return;
		}
		try {
			byte[] png = MacMenuName.currentIconPng();
			if (png == null) {
				return;
			}
			NativeImage full = NativeImage.read(png);
			// scale down icon
			NativeImage small = new NativeImage(full.format(), TEXTURE_SIZE, TEXTURE_SIZE, false);
			full.resizeSubRectTo(0, 0, full.getWidth(), full.getHeight(), small);
			full.close();
			textures.register(PREVIEW_ID, new DynamicTexture(() -> "mochaccino icon preview", small));
			hasPreview = true;
		} catch (Throwable t) {
			Mochaccino.LOGGER.warn("Could not load the icon preview", t);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		graphics.text(font, fit(iconName, MAX_NAME_WIDTH), rowLeft() + PREVIEW_SIZE + MARGIN, rowTop(), 0xFFFFFFFF);
	}

	// shortens the text with an ellipsis so it fits the width
	private String fit(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		while (!text.isEmpty() && font.width(text + "…") > maxWidth) {
			text = text.substring(0, text.length() - 1);
		}
		return text + "…";
	}

	@Override
	public void removed() {
		Minecraft.getInstance().getTextureManager().release(PREVIEW_ID);
		super.removed();
	}

	private class IconWidget extends AbstractWidget {
		IconWidget() {
			super(0, 0, PREVIEW_SIZE, PREVIEW_SIZE, Component.literal("Choose icon"));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			if (hasPreview) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, PREVIEW_ID, getX(), getY(), 0.0F, 0.0F, PREVIEW_SIZE, PREVIEW_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
			} else {
				graphics.fill(getX(), getY(), getX() + PREVIEW_SIZE, getY() + PREVIEW_SIZE, 0xFF303030);
			}
			if (active && isHoveredOrFocused()) {
				graphics.outline(getX(), getY(), PREVIEW_SIZE, PREVIEW_SIZE, 0xFFFFFFFF);
			}
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			chooseIcon();
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	// opens the system file picker; the callback comes back with the chosen file (or nothing if canceled)
	private void chooseIcon() {
		Minecraft client = Minecraft.getInstance();

		ByteBuffer filterName = MemoryUtil.memUTF8("Images");
		ByteBuffer filterPattern = MemoryUtil.memUTF8("png;jpg;jpeg;gif;tiff;icns");
		SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.calloc(1);
		filters.name(filterName).pattern(filterPattern);

		SDL_DialogFileCallback[] callback = new SDL_DialogFileCallback[1];
		callback[0] = SDL_DialogFileCallback.create((userdata, fileList, filter) -> {
			String chosen = fileList == MemoryUtil.NULL || MemoryUtil.memGetAddress(fileList) == MemoryUtil.NULL
				? null
				: MemoryUtil.memUTF8(MemoryUtil.memGetAddress(fileList));

			client.execute(() -> {
				try {
					if (chosen != null && IconSettings.applyAndSave(Path.of(chosen))) {
						refresh();
					} else if (chosen != null) {
						Mochaccino.LOGGER.warn("{} is not an image macOS can read", chosen);
					}
				} finally {
					callback[0].free();
					filters.free();
					MemoryUtil.memFree(filterName);
					MemoryUtil.memFree(filterPattern);
				}
			});
		});

		SDLDialog.SDL_ShowOpenFileDialog(callback[0], MemoryUtil.NULL, client.getWindow().handle(), filters, (CharSequence) null, false);
	}
}
