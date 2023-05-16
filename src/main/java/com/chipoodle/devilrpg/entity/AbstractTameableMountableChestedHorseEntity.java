package com.chipoodle.devilrpg.entity;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;


public abstract class AbstractTameableMountableChestedHorseEntity extends AbstractMountableTamableEntity {
   private static final EntityDataAccessor<Boolean> DATA_ID_CHEST = SynchedEntityData.defineId(AbstractTameableMountableChestedHorseEntity.class, EntityDataSerializers.BOOLEAN);

   protected AbstractTameableMountableChestedHorseEntity(EntityType<? extends AbstractTameableMountableChestedHorseEntity> p_i48564_1_, Level p_i48564_2_) {
      super(p_i48564_1_, p_i48564_2_);
      this.canGallop = false;
   }

   protected void randomizeAttributes() {
      this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(this.generateRandomMaxHealth());
   }

   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(DATA_ID_CHEST, false);
   }

   public static AttributeSupplier.Builder createBaseChestedHorseAttributes() {
      return createBaseHorseAttributes().add(Attributes.MOVEMENT_SPEED, 0.175F).add(Attributes.JUMP_STRENGTH, 0.5D);
   }

   public boolean hasChest() {
      return this.entityData.get(DATA_ID_CHEST);
   }

   public void setChest(boolean p_110207_1_) {
      this.entityData.set(DATA_ID_CHEST, p_110207_1_);
   }

   protected int getInventorySize() {
      return this.hasChest() ? 17 : super.getInventorySize();
   }

   public double getPassengersRidingOffset() {
      return super.getPassengersRidingOffset() - 0.25D;
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

   public void addAdditionalSaveData(CompoundTag p_213281_1_) {
      super.addAdditionalSaveData(p_213281_1_);
      p_213281_1_.putBoolean("ChestedHorse", this.hasChest());
      if (this.hasChest()) {
         ListTag listTag = new ListTag();

         for(int i = 2; i < this.inventory.getContainerSize(); ++i) {
            ItemStack itemstack = this.inventory.getItem(i);
            if (!itemstack.isEmpty()) {
               CompoundTag compoundTag = new CompoundTag();
               compoundTag.putByte("Slot", (byte)i);
               itemstack.save(compoundTag);
               listTag.add(compoundTag);
            }
         }

         p_213281_1_.put("Items", listTag);
      }

   }

   public void readAdditionalSaveData(CompoundTag p_70037_1_) {
      super.readAdditionalSaveData(p_70037_1_);
      this.setChest(p_70037_1_.getBoolean("ChestedHorse"));
      if (this.hasChest()) {
         ListTag listTag = p_70037_1_.getList("Items", 10);
         this.createInventory();

         for(int i = 0; i < listTag.size(); ++i) {
            CompoundTag compoundTag = listTag.getCompound(i);
            int j = compoundTag.getByte("Slot") & 255;
            if (j >= 2 && j < this.inventory.getContainerSize()) {
               this.inventory.setItem(j, ItemStack.of(compoundTag));
            }
         }
      }

      this.updateContainerEquipment();
   }

   public boolean setSlot(int p_174820_1_, ItemStack p_174820_2_) {
      if (p_174820_1_ == 499) {
         if (this.hasChest() && p_174820_2_.isEmpty()) {
            this.setChest(false);
            this.createInventory();
            return true;
         }

         if (!this.hasChest() && p_174820_2_.getItem() == Blocks.CHEST.asItem()) {
            this.setChest(true);
            this.createInventory();
            return true;
         }
      }

      return super.setSlot(p_174820_1_, p_174820_2_);
   }

   public InteractionResult mobInteract(Player p_230254_1_, InteractionHand p_230254_2_) {
      ItemStack itemstack = p_230254_1_.getItemInHand(p_230254_2_);
      if (!this.isBaby()) {
         if (this.isTame() && p_230254_1_.isSecondaryUseActive()) {
            this.openCustomInventoryScreen(p_230254_1_);
            return InteractionResult.sidedSuccess(this.level.isClientSide);
         }

         if (this.isVehicle()) {
            return super.mobInteract(p_230254_1_, p_230254_2_);
         }
      }

      if (!itemstack.isEmpty()) {
         if (this.isFood(itemstack)) {
            return this.fedFood(p_230254_1_, itemstack);
         }

         if (!this.isTame()) {
            this.makeMad();
            return InteractionResult.sidedSuccess(this.level.isClientSide);
         }

         if (!this.hasChest() && itemstack.getItem() == Blocks.CHEST.asItem()) {
            this.setChest(true);
            this.playChestEquipsSound();
            if (!p_230254_1_.getAbilities().instabuild) {
               itemstack.shrink(1);
            }

            this.createInventory();
            return InteractionResult.sidedSuccess(this.level.isClientSide);
         }

         if (!this.isBaby() && !this.isSaddled() && itemstack.getItem() == Items.SADDLE) {
            this.openCustomInventoryScreen(p_230254_1_);
            return InteractionResult.sidedSuccess(this.level.isClientSide);
         }
      }

      if (this.isBaby()) {
         return super.mobInteract(p_230254_1_, p_230254_2_);
      } else {
         this.doPlayerRide(p_230254_1_);
         return InteractionResult.sidedSuccess(this.level.isClientSide);
      }
   }

   protected void playChestEquipsSound() {
      this.playSound(SoundEvents.DONKEY_CHEST, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
   }

   public int getInventoryColumns() {
      return 5;
   }
}
