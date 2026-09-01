package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DevilRpg.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DEVILRPG_TAB = CREATIVE_TABS.register(ModItems.CREATIVE_TAB_NAME, () -> CreativeModeTab.builder()
            .title(Component.translatable("item_group." + DevilRpg.MODID + "." + ModItems.CREATIVE_TAB_NAME))
            .icon(() -> new ItemStack(ModItems.ITEM_VACIO.get()))
            .displayItems((enabledFlags, populator) -> {
                ModBlocks.BLOCKS.getEntries().forEach(blockRegistryObject -> populator.accept(blockRegistryObject.get()));
                populator.accept(ModItems.SOULWOLF_SPAWN_EGG.get());
                populator.accept(ModItems.SOULBEAR_SPAWN_EGG.get());
                populator.accept(ModItems.SOULWISP_HEALTH_SPAWN_EGG.get());
                populator.accept(ModItems.SOULWISP_ARCHER_SPAWN_EGG.get());
                populator.accept(ModItems.SOULWISP_CHOPPER_SPAWN_EGG.get());
                populator.accept(ModItems.SOULWISP_FORESTER_SPAWN_EGG.get());
                populator.accept(ModItems.AGGRESSIVE_ZOMBIE_SPAWN_EGG.get());
            })
            .build());
}
