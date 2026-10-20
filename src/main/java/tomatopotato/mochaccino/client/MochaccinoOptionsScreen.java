package tomatopotato.mochaccino.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.cursor.CursorTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.KeyEvent;
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
import java.util.ArrayList;
import java.util.List;

public class MochaccinoOptionsScreen extends OptionsSubScreen {
	private static final Component TITLE = Component.literal("Mochaccino");

	private static final Identifier PREVIEW_ID = Mochaccino.id("icon_preview");
	private static final int PREVIEW_SIZE = 48;
	private static final int TEXTURE_SIZE = 256;
	private static final int MARGIN = 8;
	private static final int LIQUID_GLASS_WIDTH = 200;
	private static final int NAME_GAP = 4;
	private static final int SWATCH_SIZE = 20;
	private static final int PATH_ROW_HEIGHT = 20;
	private static final int PATH_GAP = 4;
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
	private static final Component BACKGROUND_LABEL = Component.literal("Color");

	private IconWidget iconWidget;
	private Button resetButton;
	private CycleButton<Boolean> liquidGlassButton;
	private EditBox colorBox;
	private Button folderButton;
	private EditBox pathBox;
	private PresetRow presetRow;
	private ScaleSlider scaleSlider;
	private ContentList contentList;

	private int backgroundColor;
	private boolean hasPreview;

	public MochaccinoOptionsScreen(Screen lastScreen, Options options) {
		super(lastScreen, options, TITLE);
	}

	@Override
	protected void addOptions() {}

	@Override
	protected void addContents() {
		list = new OptionsList(minecraft, width, this);

		contentList = layout.addToContents(new ContentList(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight()));
		contentList.add(new ContentEntry(), contentHeight());
		refresh();
	}

	@Override
	protected void repositionElements() {
		super.repositionElements();
		if (contentList != null) {
			contentList.updateSize(width, layout);
		}
	}

	private int contentHeight() {
		return MARGIN + PREVIEW_SIZE + NAME_GAP + 20 + MARGIN + PATH_ROW_HEIGHT + MARGIN + 20 + MARGIN + 20 + MARGIN
			+ PRESET_SIZE + 4 + font.lineHeight + MARGIN + 20 + MARGIN;
	}

	private class ContentList extends ContainerObjectSelectionList<ContentEntry> {
		ContentList(Minecraft minecraft, int width, int height, int y) {
			super(minecraft, width, height, y, 20);
			// start at the top even when everything fits
			centerListVertically = false;
		}

		void add(ContentEntry entry, int height) {
			addEntry(entry, height);
		}
	}

	// the whole page as one tall entry. the list takes care of scrolling it, clipping it and the scrollbar
	private class ContentEntry extends ContainerObjectSelectionList.Entry<ContentEntry> {
		private final List<AbstractWidget> widgets = new ArrayList<>();

		ContentEntry() {
			iconWidget = new IconWidget();
			resetButton = Button.builder(Component.literal("Reset"), button -> IconSettings.reset(MochaccinoOptionsScreen.this::refresh))
				.width(PREVIEW_SIZE).build();

			folderButton = Button.builder(Component.empty(), button -> chooseIcon()).size(PATH_ROW_HEIGHT, PATH_ROW_HEIGHT).build();
			folderButton.active = Mochaccino.isMacOS();
			pathBox = new EditBox(font, 0, 0, LIQUID_GLASS_WIDTH - PATH_ROW_HEIGHT - PATH_GAP, PATH_ROW_HEIGHT, Component.literal("Icon path")) {

				@Override
				public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
					super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
					if (isHovered()) {
						graphics.requestCursor(CursorTypes.IBEAM);
					}
				}
			};
			pathBox.setMaxLength(4096);
			pathBox.setHint(Component.literal("Default"));
			pathBox.setEditable(false);
			pathBox.setTextColorUneditable(0xFFFFFFFF);

			liquidGlassButton = CycleButton.onOffBuilder(IconSettings.useLiquidGlass())
				.create(0, 0, LIQUID_GLASS_WIDTH, 20, Component.literal("Icon background"),
					(button, value) -> {
						IconSettings.setUseLiquidGlass(value);
						colorBox.setEditable(value);
						presetRow.active = value;
						scaleSlider.active = value;
						IconSettings.applyNow(MochaccinoOptionsScreen.this::refresh);
					});

			backgroundColor = IconSettings.backgroundColor();
			colorBox = new EditBox(font, 0, 0, COLOR_FIELD_WIDTH, 20, Component.literal("Background color"));
			colorBox.setMaxLength(7);
			colorBox.setHint(Component.literal("#RRGGBB"));
			colorBox.setValue(String.format("#%06X", backgroundColor));
			colorBox.setEditable(IconSettings.useLiquidGlass());
			colorBox.setResponder(MochaccinoOptionsScreen.this::colorTyped);

			presetRow = new PresetRow();
			presetRow.active = IconSettings.useLiquidGlass();

			scaleSlider = new ScaleSlider();
			scaleSlider.active = IconSettings.useLiquidGlass();

			widgets.addAll(List.of(resetButton, folderButton, pathBox, liquidGlassButton, colorBox, presetRow, scaleSlider));
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return widgets;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return widgets;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			int centerX = getContentXMiddle();
			int top = getContentY() + MARGIN;

			iconWidget.setX(centerX - PREVIEW_SIZE / 2);
			iconWidget.setY(top);
			resetButton.setX(iconWidget.getX());
			resetButton.setY(top + PREVIEW_SIZE + NAME_GAP);

			int pathY = resetButton.getY() + 20 + MARGIN;
			folderButton.setX(centerX - LIQUID_GLASS_WIDTH / 2);
			folderButton.setY(pathY);
			pathBox.setX(folderButton.getX() + PATH_ROW_HEIGHT + PATH_GAP);
			pathBox.setY(pathY);
			liquidGlassButton.setX(centerX - LIQUID_GLASS_WIDTH / 2);
			liquidGlassButton.setY(pathY + PATH_ROW_HEIGHT + MARGIN);

			int labelWidth = font.width(BACKGROUND_LABEL);
			int colorRowX = centerX - (labelWidth + MARGIN + SWATCH_SIZE + MARGIN + COLOR_FIELD_WIDTH) / 2;
			int colorY = liquidGlassButton.getY() + 20 + MARGIN;
			int swatchX = colorRowX + labelWidth + MARGIN;
			colorBox.setX(swatchX + SWATCH_SIZE + MARGIN);
			colorBox.setY(colorY);
			presetRow.setX(centerX - presetRow.getWidth() / 2);
			presetRow.setY(colorY + 20 + MARGIN);
			scaleSlider.setX(centerX - LIQUID_GLASS_WIDTH / 2);
			scaleSlider.setY(presetRow.getY() + PRESET_SIZE + 4 + font.lineHeight + MARGIN);

			iconWidget.extractRenderState(graphics, mouseX, mouseY, partialTick);
			for (AbstractWidget widget : widgets) {
				widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
			}

			drawFolder(graphics, folderButton.getX() + (folderButton.getWidth() - 14) / 2,
				folderButton.getY() + (folderButton.getHeight() - 11) / 2, folderButton.active);

			boolean enabled = IconSettings.useLiquidGlass();
			graphics.text(font, BACKGROUND_LABEL, colorRowX, colorY + (20 - font.lineHeight) / 2, enabled ? 0xFFFFFFFF : 0xFF707070);
			graphics.fill(swatchX, colorY, swatchX + SWATCH_SIZE, colorY + SWATCH_SIZE, 0xFF000000 | backgroundColor);
			graphics.outline(swatchX, colorY, SWATCH_SIZE, SWATCH_SIZE, enabled ? 0xFFFFFFFF : 0xFF707070);
		}
	}

	private void refresh() {
		Path custom = IconSettings.customIcon();
		resetButton.active = Mochaccino.isMacOS() && custom != null;
		pathBox.setValue(custom == null ? "" : custom.toString());
		pathBox.moveCursorToStart(false);
		refreshPreview();
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

	private static void drawFolder(GuiGraphicsExtractor graphics, int x, int y, boolean active) {
		int back = active ? 0xFFC9962B : 0xFF6B5A30;
		int front = active ? 0xFFF2C14E : 0xFF8A7440;
		graphics.fill(x, y, x + 6, y + 3, back);
		graphics.fill(x, y + 2, x + 14, y + 11, back);
		graphics.fill(x, y + 4, x + 14, y + 11, front);
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

	private class ScaleSlider extends AbstractSliderButton {
		ScaleSlider() {
			super(0, 0, LIQUID_GLASS_WIDTH, 20, Component.empty(),
				(IconSettings.iconScale() - IconSettings.MIN_SCALE) / (IconSettings.MAX_SCALE - IconSettings.MIN_SCALE));
			updateMessage();
		}

		private double scale() {
			double scale = IconSettings.MIN_SCALE + value * (IconSettings.MAX_SCALE - IconSettings.MIN_SCALE);
			return Math.round(scale * 100) / 100.0;
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.literal("Icon scale: " + Math.round(scale() * 100) + "%"));
		}

		@Override
		protected void applyValue() {
			IconSettings.setIconScale(scale());
		}

		// making the icon takes a moment, so it is redone once the slider is let go instead of on every step
		@Override
		public void onRelease(MouseButtonEvent event) {
			super.onRelease(event);
			IconSettings.applyNow(MochaccinoOptionsScreen.this::refresh);
		}

		@Override
		public boolean keyPressed(KeyEvent event) {
			double before = value;
			boolean handled = super.keyPressed(event);
			if (value != before) {
				IconSettings.applyNow(MochaccinoOptionsScreen.this::refresh);
			}
			return handled;
		}
	}

	private class IconWidget extends AbstractWidget {
		IconWidget() {
			super(0, 0, PREVIEW_SIZE, PREVIEW_SIZE, Component.literal("Icon preview"));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			if (hasPreview) {
				graphics.blit(RenderPipelines.GUI_TEXTURED, PREVIEW_ID, getX(), getY(), 0.0F, 0.0F, PREVIEW_SIZE, PREVIEW_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
			} else {
				graphics.fill(getX(), getY(), getX() + PREVIEW_SIZE, getY() + PREVIEW_SIZE, 0xFF303030);
			}
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
