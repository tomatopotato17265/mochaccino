package tomatopotato.mochaccino.client;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
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
	private static final int LIQUID_GLASS_WIDTH = 200;
	private static final int NAME_GAP = 3;
	private static final int SWATCH_SIZE = 20;
	private static final int COLOR_FIELD_WIDTH = 70;
	private static final int PRESET_SIZE = 16;
	private static final int PRESET_GAP = 4;
	private static final Preset[] PRESETS = {
		new Preset("White", 0xFFFFFF),
		new Preset("macOS Dark", 0x161616),
		new Preset("Black", 0x000000),
		new Preset("Gray", 0x8E8E93),
		new Preset("Red", 0xFF3B30),
		new Preset("Orange", 0xFF9500),
		new Preset("Yellow", 0xFFCC00),
		new Preset("Green", 0x34C759),
		new Preset("Blue", 0x007AFF),
		new Preset("Purple", 0xAF52DE),
		new Preset("Pink", 0xFF2D55),
	};

	private record Preset(String name, int rgb) {
	}
	private static final Component BACKGROUND_LABEL = Component.literal("Background");

	private IconWidget iconWidget;
	private Button resetButton;
	private CycleButton<Boolean> liquidGlassButton;
	private EditBox colorBox;
	private PresetRow presetRow;

	private int backgroundColor;
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
		liquidGlassButton = null;
		colorBox = null;
		presetRow = null;
		super.init();

		iconWidget = addRenderableWidget(new IconWidget());
		resetButton = addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
			IconSettings.reset(this::refresh);
			refresh();
		}).width(RESET_WIDTH).build());

		liquidGlassButton = addRenderableWidget(CycleButton.onOffBuilder(IconSettings.useLiquidGlass())
			.create(0, 0, LIQUID_GLASS_WIDTH, 20, Component.literal("Use Liquid Glass icon"),
				(button, value) -> {
					IconSettings.setUseLiquidGlass(value);
					colorBox.setEditable(value);
					presetRow.active = value;
					IconSettings.applyNow(this::refresh);
				}));

		backgroundColor = IconSettings.backgroundColor();
		colorBox = addRenderableWidget(new EditBox(font, 0, 0, COLOR_FIELD_WIDTH, 20, Component.literal("Background color")));
		colorBox.setMaxLength(7);
		colorBox.setHint(Component.literal("#RRGGBB"));
		colorBox.setValue(String.format("#%06X", backgroundColor));
		colorBox.setEditable(IconSettings.useLiquidGlass());
		colorBox.setResponder(this::colorTyped);

		presetRow = addRenderableWidget(new PresetRow());
		presetRow.active = IconSettings.useLiquidGlass();

		iconWidget.active = Mochaccino.isMacOS();
		placeWidgets();
		refresh();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (presetRow != null && presetRow.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (colorBox != null) {
			if (colorBox.mouseClicked(event, doubleClick)) {
				setFocused(colorBox);
				return true;
			}
			colorBox.setFocused(false);
		}
		if (iconWidget != null && iconWidget.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (resetButton != null && resetButton.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (liquidGlassButton != null && liquidGlassButton.mouseClicked(event, doubleClick)) {
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
		if (iconWidget == null || resetButton == null || liquidGlassButton == null || colorBox == null || presetRow == null) {
			return;
		}
		iconWidget.setX(rowLeft());
		iconWidget.setY(rowTop());
		resetButton.setX(rowLeft() + PREVIEW_SIZE + MARGIN);
		resetButton.setY(rowTop() + font.lineHeight + NAME_GAP);
		liquidGlassButton.setX((width - LIQUID_GLASS_WIDTH) / 2);
		liquidGlassButton.setY(rowTop() + PREVIEW_SIZE + MARGIN * 2);

		int rowWidth = font.width(BACKGROUND_LABEL) + MARGIN + SWATCH_SIZE + MARGIN + COLOR_FIELD_WIDTH;
		int colorY = liquidGlassButton.getY() + 20 + MARGIN;
		colorBox.setX((width - rowWidth) / 2 + font.width(BACKGROUND_LABEL) + MARGIN + SWATCH_SIZE + MARGIN);
		colorBox.setY(colorY);
		presetRow.setX((width - presetRow.getWidth()) / 2);
		presetRow.setY(colorY + 20 + MARGIN);
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

		if (colorBox != null) {
			boolean enabled = IconSettings.useLiquidGlass();
			int labelX = (width - (font.width(BACKGROUND_LABEL) + MARGIN + SWATCH_SIZE + MARGIN + COLOR_FIELD_WIDTH)) / 2;
			int swatchX = labelX + font.width(BACKGROUND_LABEL) + MARGIN;
			graphics.text(font, BACKGROUND_LABEL, labelX, colorBox.getY() + (20 - font.lineHeight) / 2, enabled ? 0xFFFFFFFF : 0xFF707070);
			graphics.fill(swatchX, colorBox.getY(), swatchX + SWATCH_SIZE, colorBox.getY() + SWATCH_SIZE, 0xFF000000 | backgroundColor);
			graphics.outline(swatchX, colorBox.getY(), SWATCH_SIZE, SWATCH_SIZE, enabled ? 0xFFFFFFFF : 0xFF707070);
		}
	}

	private void colorTyped(String text) {
		String hex = text.startsWith("#") ? text.substring(1) : text;
		if (hex.length() != 6) {
			return;
		}
		int color;
		try {
			color = Integer.parseInt(hex, 16);
		} catch (NumberFormatException e) {
			return;
		}
		if (color == backgroundColor) {
			return;
		}
		backgroundColor = color;
		IconSettings.setBackgroundColor(color);
		IconSettings.applyNow(this::refresh);
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

	private class PresetRow extends AbstractWidget {
		PresetRow() {
			super(0, 0, PRESETS.length * PRESET_SIZE + (PRESETS.length - 1) * PRESET_GAP, PRESET_SIZE, Component.literal("Background presets"));
		}

		private int presetAt(double x, double y) {
			if (y < getY() || y >= getY() + PRESET_SIZE || x < getX()) {
				return -1;
			}
			int offset = (int) (x - getX());
			int index = offset / (PRESET_SIZE + PRESET_GAP);
			return index < PRESETS.length && offset % (PRESET_SIZE + PRESET_GAP) < PRESET_SIZE ? index : -1;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int hovered = active ? presetAt(mouseX, mouseY) : -1;
			for (int i = 0; i < PRESETS.length; i++) {
				int x = getX() + i * (PRESET_SIZE + PRESET_GAP);
				graphics.fill(x, getY(), x + PRESET_SIZE, getY() + PRESET_SIZE, 0xFF000000 | PRESETS[i].rgb());
				boolean selected = PRESETS[i].rgb() == backgroundColor;

				graphics.outline(x, getY(), PRESET_SIZE, PRESET_SIZE, !active ? 0xFF505050 : selected || i == hovered ? 0xFFFFFFFF : 0xFF707070);
			}
			if (hovered >= 0) {
				graphics.centeredText(font, PRESETS[hovered].name(), getX() + getWidth() / 2, getY() + PRESET_SIZE + 4, 0xFFFFFFFF);
			}
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			int index = presetAt(event.x(), event.y());
			if (index >= 0) {
				// typing the color in is how every other change is applied, so this does the same
				colorBox.setValue(String.format("#%06X", PRESETS[index].rgb()));
			}
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			return active && presetAt(event.x(), event.y()) >= 0 && super.mouseClicked(event, doubleClick);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
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
					if (chosen != null && IconSettings.applyAndSave(Path.of(chosen), this::refresh)) {
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
