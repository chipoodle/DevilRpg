package com.chipoodle.devilrpg.entity.container;

import com.chipoodle.devilrpg.entity.AbstractChestedMountablePet;
import com.chipoodle.devilrpg.entity.AbstractMountablePet;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;


public class MountablePetContainerMenu extends AbstractContainerMenu {
    private final Container horseContainer;
    private final AbstractMountablePet horse;

    public MountablePetContainerMenu(int p_39656_, Inventory inventory, Container container, final AbstractMountablePet p_39659_) {
        super(null, p_39656_);
        this.horseContainer = container;
        this.horse = p_39659_;
        int i = 3;
        container.startOpen(inventory.player);
        int j = -18;
        this.addSlot(new Slot(container, 0, 8, 18) {
            public boolean mayPlace(@NotNull ItemStack itemStack) {
                return itemStack.is(Items.SADDLE) && !this.hasItem() && p_39659_.isSaddleable();
            }

            public boolean isActive() {
                return p_39659_.isSaddleable();
            }
        });
        this.addSlot(new Slot(container, 1, 8, 36) {
            public boolean mayPlace(ItemStack p_39690_) {
                return p_39659_.isArmor(p_39690_);
            }

            public boolean isActive() {
                return p_39659_.canWearArmor();
            }

            public int getMaxStackSize() {
                return 1;
            }
        });
        if (this.hasChest(p_39659_)) {
            for(int k = 0; k < 3; ++k) {
                for(int l = 0; l < ((AbstractChestedMountablePet)p_39659_).getInventoryColumns(); ++l) {
                    this.addSlot(new Slot(container, 2 + l + k * ((AbstractChestedMountablePet)p_39659_).getInventoryColumns(), 80 + l * 18, 18 + k * 18));
                }
            }
        }

        for(int i1 = 0; i1 < 3; ++i1) {
            for(int k1 = 0; k1 < 9; ++k1) {
                this.addSlot(new Slot(inventory, k1 + i1 * 9 + 9, 8 + k1 * 18, 102 + i1 * 18 - 18));
            }
        }

        for(int j1 = 0; j1 < 9; ++j1) {
            this.addSlot(new Slot(inventory, j1, 8 + j1 * 18, 142));
        }

    }

    public boolean stillValid(Player p_39661_) {
        return !this.horse.hasInventoryChanged(this.horseContainer) && this.horseContainer.stillValid(p_39661_) && this.horse.isAlive() && this.horse.distanceTo(p_39661_) < 8.0F;
    }

    private boolean hasChest(AbstractMountablePet p_150578_) {
        return p_150578_ instanceof AbstractChestedMountablePet && ((AbstractChestedMountablePet)p_150578_).hasChest();
    }

    public @NotNull ItemStack quickMoveStack(@NotNull Player p_39665_, int p_39666_) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(p_39666_);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            int i = this.horseContainer.getContainerSize();
            if (p_39666_ < i) {
                if (!this.moveItemStackTo(itemstack1, i, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(1).mayPlace(itemstack1) && !this.getSlot(1).hasItem()) {
                if (!this.moveItemStackTo(itemstack1, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(0).mayPlace(itemstack1)) {
                if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (i <= 2 || !this.moveItemStackTo(itemstack1, 2, i, false)) {
                int j = i + 27;
                int k = j + 9;
                if (p_39666_ >= j && p_39666_ < k) {
                    if (!this.moveItemStackTo(itemstack1, i, j, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (p_39666_ >= i && p_39666_ < j) {
                    if (!this.moveItemStackTo(itemstack1, j, k, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, j, j, false)) {
                    return ItemStack.EMPTY;
                }

                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    public void removed(@NotNull Player p_39663_) {
        super.removed(p_39663_);
        this.horseContainer.stopOpen(p_39663_);
    }
}
