package tomatopotato.mochaccino.mixin;

import java.util.List;

import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PackSelectionModel.class)
public interface PackSelectionModelAccessor {
	@Accessor("selected")
	List<Pack> mochaccino$selected();

	@Accessor("repository")
	PackRepository mochaccino$repository();
}
