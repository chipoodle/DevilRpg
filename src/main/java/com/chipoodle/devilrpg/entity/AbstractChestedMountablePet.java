package com.chipoodle.devilrpg.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class AbstractChestedMountablePet extends AbstractMountablePet {
    private static final EntityDataAccessor<Boolean> DATA_ID_CHEST = SynchedEntityData.defineId(AbstractChestedMountablePet.class, EntityDataSerializers.BOOLEAN);
    public static final int INV_CHEST_COUNT = 15;

    protected AbstractChestedMountablePet(EntityType<? extends AbstractChestedMountablePet> p_30485_, Level p_30486_) {
        super(p_30485_, p_30486_);
        this.canGallop = false;
    }

    protected void randomizeAttributes(RandomSource p_218803_) {
        Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue((double)generateMaxHealth(p_218803_::nextInt));
    }

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ID_CHEST, false);
    }

    public static AttributeSupplier.Builder createBaseChestedHorseAttributes() {
        return createBaseHorseAttributes().add(Attributes.MOVEMENT_SPEED, (double)0.175F).add(Attributes.JUMP_STRENGTH, 0.5D);
    }

    public boolean hasChest() {
        return this.entityData.get(DATA_ID_CHEST);
    }

    public void setChest(boolean p_30505_) {
        this.entityData.set(DATA_ID_CHEST, p_30505_);
    }

    protected int getInventorySize() {
        return this.hasChest() ? 17 : super.getInventorySize();
    }

    protected float getPassengersRidingOffsetY(EntityDimensions p_298002_, float p_300957_) {
        return p_298002_.height - (this.isBaby() ? 0.15625F : 0.3875F) * p_300957_;
    }

    protected void dropEquipment() {
        super.dropEquipment();
        if (this.hasChest()) {
            if (!this.level.isClientSide) {
                this.spawnAtLocation(Blocks.CHEST);
            }

            this.setChest(false);
        }

    }

    public void addAdditionalSaveData(CompoundTag p_30496_) {
        super.addAdditionalSaveData(p_30496_);
        p_30496_.putBoolean("ChestedHorse", this.hasChest());
        if (this.hasChest()) {
            ListTag listtag = new ListTag();

            for(int i = 2; i < this.inventory.getContainerSize(); ++i) {
                ItemStack itemstack = this.inventory.getItem(i);
                if (!itemstack.isEmpty()) {
                    CompoundTag compoundtag = new CompoundTag();
                    compoundtag.putByte("Slot", (byte)i);
                    itemstack.save(compoundtag);
                    listtag.add(compoundtag);
                }
            }

            p_30496_.put("Items", listtag);
        }

    }

    public void readAdditionalSaveData(@NotNull CompoundTag p_30488_) {
        super.readAdditionalSaveData(p_30488_);
        this.setChest(p_30488_.getBoolean("ChestedHorse"));
        this.createInventory();
        if (this.hasChest()) {
            ListTag listtag = p_30488_.getList("Items", 10);

            for(int i = 0; i < listtag.size(); ++i) {
                CompoundTag compoundtag = listtag.getCompound(i);
                int j = compoundtag.getByte("Slot") & 255;
                if (j >= 2 && j < this.inventory.getContainerSize()) {
                    this.inventory.setItem(j, ItemStack.of(compoundtag));
                }
            }
        }

        this.updateContainerEquipment();
    }

    public SlotAccess getSlot(int p_149479_) {
        return p_149479_ == 499 ? new SlotAccess() {
            public ItemStack get() {
                return AbstractChestedMountablePet.this.hasChest() ? new ItemStack(Items.CHEST) : ItemStack.EMPTY;
            }

            public boolean set(ItemStack p_149485_) {
                if (p_149485_.isEmpty()) {
                    if (AbstractChestedMountablePet.this.hasChest()) {
                        AbstractChestedMountablePet.this.setChest(false);
                        AbstractChestedMountablePet.this.createInventory();
                    }

                    return true;
                } else if (p_149485_.is(Items.CHEST)) {
                    if (!AbstractChestedMountablePet.this.hasChest()) {
                        AbstractChestedMountablePet.this.setChest(true);
                        AbstractChestedMountablePet.this.createInventory();
                    }

                    return true;
                } else {
                    return false;
                }
            }
        } : super.getSlot(p_149479_);
    }

    public InteractionResult mobInteract(Player p_30493_, InteractionHand p_30494_) {
        boolean flag = !this.isBaby() && this.isTame() && p_30493_.isSecondaryUseActive();
        if (!this.isVehicle() && !flag) {
            ItemStack itemstack = p_30493_.getItemInHand(p_30494_);
            if (!itemstack.isEmpty()) {
                if (this.isFood(itemstack)) {
                    return this.fedFood(p_30493_, itemstack);
                }

                if (!this.isTame()) {
                    this.makeMad();
                    return InteractionResult.sidedSuccess(this.level.isClientSide);
                }

                if (!this.hasChest() && itemstack.is(Items.CHEST)) {
                    this.equipChest(p_30493_, itemstack);
                    return InteractionResult.sidedSuccess(this.level.isClientSide);
                }
            }

            return super.mobInteract(p_30493_, p_30494_);
        } else {
            return super.mobInteract(p_30493_, p_30494_);
        }
    }

    private void equipChest(Player p_250937_, ItemStack p_251558_) {
        this.setChest(true);
        this.playChestEquipsSound();
        if (!p_250937_.getAbilities().instabuild) {
            p_251558_.shrink(1);
        }

        this.createInventory();
    }

    protected void playChestEquipsSound() {
        this.playSound(SoundEvents.DONKEY_CHEST, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
    }

    public int getInventoryColumns() {
        return 5;
    }
}