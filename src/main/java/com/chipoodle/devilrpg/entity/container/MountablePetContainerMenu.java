package com.chipoodle.devilrpg.entity.container;

import net.minecraft.world.Container;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;


public class MountablePetContainerMenu extends AbstractContainerMenu {

    private final Container horseContainer;
    private final AbstractHorse horse;

    public MountablePetContainerMenu(int i, Inventory inventory) {
        super(null, i);
        horseContainer = null;
        horse = null;

    }

    public MountablePetContainerMenu(int id, Inventory inventory, Container container, final AbstractHorse abstractHorse) {
        super((MenuType<?>) null, id);
        this.horseContainer = container;
        this.horse = abstractHorse;
        int i = 3;
        container.startOpen(inventory.player);
        int j = -18;
        this.addSlot(new Slot(container, 0, 8, 18) {
            public boolean mayPlace(@NotNull ItemStack p_39677_) {
                return p_39677_.is(Items.SADDLE) && !this.hasItem() && abstractHorse.isSaddleable();
            }

            public boolean isActive() {
                return abstractHorse.isSaddleable();
            }
        });
        this.addSlot(new Slot(container, 1, 8, 36) {
            public boolean mayPlace(@NotNull ItemStack itemStack) {
                return !itemStack.isEmpty();
            }

            public boolean isActive() {
                return true;
            }

            public int getMaxStackSize() {
                return 1;
            }
        });
        if (this.hasChest(abstractHorse)) {
            for (int k = 0; k < 3; ++k) {
                for (int l = 0; l < ((AbstractChestedHorse) abstractHorse).getInventoryColumns(); ++l) {
                    this.addSlot(new Slot(container, 2 + l + k * ((AbstractChestedHorse) abstractHorse).getInventoryColumns(), 80 + l * 18, 18 + k * 18));
                }
            }
        }

        for (int i1 = 0; i1 < 3; ++i1) {
            for (int k1 = 0; k1 < 9; ++k1) {
                this.addSlot(new Slot(inventory, k1 + i1 * 9 + 9, 8 + k1 * 18, 102 + i1 * 18 + -18));
            }
        }

        for (int j1 = 0; j1 < 9; ++j1) {
            this.addSlot(new Slot(inventory, j1, 8 + j1 * 18, 142));
        }

    }

    public boolean stillValid(@NotNull Player player) {
        return !this.horse.hasInventoryChanged(this.horseContainer) && this.horseContainer.stillValid(player) && this.horse.isAlive() && this.horse.distanceTo(player) < 8.0F;
    }

    private boolean hasChest(AbstractHorse abstractHorse) {
        return abstractHorse instanceof AbstractChestedHorse && ((AbstractChestedHorse) abstractHorse).hasChest();
    }

    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int id) {
        if (id == 0)
            return ItemStack.EMPTY;

        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(id);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            int i = this.horseContainer.getContainerSize();
            if (id < i) {
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
                if (id >= j && id < k) {
                    if (!this.moveItemStackTo(itemstack1, i, j, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (id >= i && id < j) {
                    if (!this.moveItemStackTo(itemstack1, j, k, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, j, j, false)) {
                    return ItemStack.EMPTY;
                }

                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    public void removed(@NotNull Player player) {
        super.removed(player);
        this.horseContainer.stopOpen(player);
    }

    @Override
    public void clicked(int i, int p_150401_, @NotNull ClickType clickType, @NotNull Player player) {
        //If it is saddle (index 0) return without performing any action
        if (i == 0)
            return;
        super.clicked(i, p_150401_, clickType, player);
    }
}
