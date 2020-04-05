/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.chipoodle.devilrpg.init;

import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.chipoodle.devilrpg.DevilRpg;

import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;

/**
 *
 * @author Christian
 */
public class ModItemGroups {

    public static final ItemGroup MOD_ITEM_GROUP = new ModItemGroup(DevilRpg.MODID, () -> new ItemStack(ModItems.PORTAL_ITEM.get()));

    public static class ModItemGroup extends ItemGroup {

        @Nonnull
        private final Supplier<ItemStack> iconSupplier;

        public ModItemGroup(final String name, final Supplier<ItemStack> iconSupplier) {
            super(name);
            this.iconSupplier = iconSupplier;
        }

        @Override
        @Nonnull
        public ItemStack createIcon() {
            return iconSupplier.get();
        }

    }
}
