package tomatopotato.mochaccino.client;

import net.fabricmc.fabric.impl.resource.pack.FabricPack;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class ResourcePacksMenu {
	private ResourcePacksMenu() {
	}

	public static void install(Minecraft client) {
		long menu = MacMenuName.addFileSubmenu("Resource Packs");
		if (menu == 0L) {
			return;
		}

		populate(client, menu);
		// resourcepacks can change from the in-game screen or the folder, so look again every time the menu opens
		MacMenuName.setMenuOpenListener(menu, () -> {
			client.getResourcePackRepository().reload();
			MacMenuName.removeAllItems(menu);
			populate(client, menu);
		});
	}

	private static void populate(Minecraft client, long menu) {
		PackRepository repository = client.getResourcePackRepository();
		Set<String> selected = Set.copyOf(repository.getSelectedIds());

		// filter server-specific and Fabric resourcepacks
		List<Pack> packs = repository.getAvailablePacks().stream()
			.filter(pack -> pack.getPackSource() != PackSource.SERVER)
			.filter(pack -> !(pack instanceof FabricPack fabricPack && fabricPack.fabric$isHidden()))
			.sorted(Comparator.comparing((Pack pack) -> !pack.isRequired())
				.thenComparing(pack -> pack.getTitle().getString().toLowerCase()))
			.toList();

		for (Pack pack : packs) {
			String title = pack.getTitle().getString();
			if (!pack.getCompatibility().isCompatible()) {
				title += " (incompatible)";
			}
			// a required pack can't be switched off, so it has no click handler and shows greyed out
			Runnable onClick = pack.isRequired() ? null : () -> toggle(client, pack.getId());
			MacMenuName.addCheckItem(menu, title, selected.contains(pack.getId()) || pack.isRequired(), onClick);
		}
	}

	private static void toggle(Minecraft client, String id) {
		client.execute(() -> {
			PackRepository repository = client.getResourcePackRepository();
			if (repository.getSelectedIds().contains(id)) {
				repository.removePack(id);
			} else {
				repository.addPack(id);
			}
			// saves the options and reloads the resources if the selection changed
			client.options.updateResourcePacks(repository);
			// an open pack screen still shows the old selection, and Done would write that back
			if (client.gui.screen() instanceof PackScreenRefresh screen) {
				screen.mochaccino$refresh();
			}
		});
	}
}
