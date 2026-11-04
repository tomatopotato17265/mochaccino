package tomatopotato.mochaccino.mixin;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.server.packs.repository.Pack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import tomatopotato.mochaccino.client.PackScreenRefresh;

@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin implements PackScreenRefresh {
	@Shadow
	private PackSelectionModel model;

	@Shadow
	private void reload() {
	}

	@Override
	public void mochaccino$refresh() {
		PackSelectionModelAccessor accessor = (PackSelectionModelAccessor) model;
		List<Pack> selected = accessor.mochaccino$selected();
		selected.clear();
		selected.addAll(accessor.mochaccino$repository().getSelectedPacks());
		Collections.reverse(selected);
		reload();
	}
}
