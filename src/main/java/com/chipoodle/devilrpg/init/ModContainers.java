package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.container.MountablePetContainerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModContainers {
    public static final DeferredRegister<MenuType<?>> CONTAINERS = DeferredRegister.create(Registries.MENU, DevilRpg.MODID);

    // El MenuType usa IMenuTypeExtension para que el CLIENTE reciba las columnas del inventario del oso en
    // el paquete de apertura (openMenu(provider, bufferConsumer)) y cree un placeholder del MISMO tamano
    // que el servidor. Asi el tamano dinamico (mitad/normal) queda sincronizado y sin crashes.
    public static final DeferredHolder<MenuType<?>, MenuType<MountablePetContainerMenu>> MOUNTABLE_PET_MENU =
            CONTAINERS.register("mountable_pet",
                    () -> IMenuTypeExtension.create(
                            (IContainerFactory<MountablePetContainerMenu>) (id, inv, data) ->
                                    new MountablePetContainerMenu(id, inv, data.readInt())));
}
