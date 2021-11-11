package com.chipoodle.devilrpg.entity.container;

import com.chipoodle.devilrpg.entity.AbstractMountableTameableEntity;
import com.chipoodle.devilrpg.entity.AbstractTameableMountableChestedHorseEntity;
import com.chipoodle.devilrpg.init.ModContainerTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.network.IContainerFactory;

public class TameableMountableHorseInventoryContainer extends Container {
    private final IInventory horseContainer;
    final AbstractMountableTameableEntity horse;

    public TameableMountableHorseInventoryContainer(int containerId, PlayerInventory playerInv, PacketBuffer buffer) {
        this(containerId, playerInv, (AbstractMountableTameableEntity) playerInv.player.level.getEntity(buffer.readInt()));
    }

    public TameableMountableHorseInventoryContainer(int containerIdIn, PlayerInventory playerInventory) {
        super(ModContainerTypes.TAMEABLE_MOUNTABLE_CONTAINER.get(), containerIdIn);
        horse = null;
        horseContainer = playerInventory;

    }

    public TameableMountableHorseInventoryContainer(int containerIdIn, PlayerInventory playerInventoryIn, AbstractMountableTameableEntity mountableTameable) {
        super(ModContainerTypes.TAMEABLE_MOUNTABLE_CONTAINER.get(), containerIdIn);
        horse = mountableTameable;
        horseContainer = playerInventoryIn;
    }

    public TameableMountableHorseInventoryContainer(int ID, PlayerInventory playerInventory,
                                                    IInventory container, final AbstractMountableTameableEntity tameableMountable) {
        super((ContainerType<?>) null, ID);
        this.horseContainer = container;
        this.horse = tameableMountable;
        int i = 3;
        container.startOpen(playerInventory.player);
        int j = -18;
        this.addSlot(new Slot(container, 0, 8, 18) {
            public boolean mayPlace(ItemStack p_75214_1_) {
                return p_75214_1_.getItem() == Items.SADDLE && !this.hasItem() && tameableMountable.isSaddleable();
            }

            @OnlyIn(Dist.CLIENT)
            public boolean isActive() {
                return tameableMountable.isSaddleable();
            }
        });
        this.addSlot(new Slot(container, 1, 8, 36) {
            public boolean mayPlace(ItemStack p_75214_1_) {
                return tameableMountable.isArmor(p_75214_1_);
            }

            @OnlyIn(Dist.CLIENT)
            public boolean isActive() {
                return tameableMountable.canWearArmor();
            }

            public int getMaxStackSize() {
                return 1;
            }
        });
        if (tameableMountable instanceof AbstractTameableMountableChestedHorseEntity
                && ((AbstractTameableMountableChestedHorseEntity) tameableMountable).hasChest()) {
            for (int k = 0; k < 3; ++k) {
                for (int l = 0; l < ((AbstractTameableMountableChestedHorseEntity) tameableMountable)
                        .getInventoryColumns(); ++l) {
                    this.addSlot(new Slot(container,
                            2 + l + k
                                    * ((AbstractTameableMountableChestedHorseEntity) tameableMountable).getInventoryColumns(),
                            80 + l * 18, 18 + k * 18));
                }
            }
        }

        for (int i1 = 0; i1 < 3; ++i1) {
            for (int k1 = 0; k1 < 9; ++k1) {
                this.addSlot(new Slot(playerInventory, k1 + i1 * 9 + 9, 8 + k1 * 18, 102 + i1 * 18 + -18));
            }
        }

        for (int j1 = 0; j1 < 9; ++j1) {
            this.addSlot(new Slot(playerInventory, j1, 8 + j1 * 18, 142));
        }

    }

    public boolean stillValid(PlayerEntity p_75145_1_) {
        return this.horseContainer.stillValid(p_75145_1_) && this.horse.isAlive()
                && this.horse.distanceTo(p_75145_1_) < 8.0F;
    }

    public ItemStack quickMoveStack(PlayerEntity p_82846_1_, int p_82846_2_) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(p_82846_2_);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            int i = this.horseContainer.getContainerSize();
            if (p_82846_2_ < i) {
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
                if (p_82846_2_ >= j && p_82846_2_ < k) {
                    if (!this.moveItemStackTo(itemstack1, i, j, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (p_82846_2_ >= i && p_82846_2_ < j) {
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

    public void removed(PlayerEntity p_75134_1_) {
        super.removed(p_75134_1_);
        this.horseContainer.stopOpen(p_75134_1_);
    }
}
