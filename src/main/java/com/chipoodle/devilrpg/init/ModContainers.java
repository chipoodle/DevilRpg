package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModContainers {
    public static final DeferredRegister<MenuType<?>> CONTAINERS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, DevilRpg.MODID);
    /*public static final RegistryObject<MenuType<MountablePetContainerMenu>> MOUNTABLE_PET_CONTAINER_MENU =
            CONTAINERS.register("mountable_pet_container_menu", () ->
                    new MenuType<>(MountablePetContainerMenu::new, FeatureFlags.DEFAULT_FLAGS));*/
}
