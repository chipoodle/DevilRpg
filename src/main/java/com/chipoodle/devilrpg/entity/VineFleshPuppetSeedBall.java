package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.effects.MobEffectVineFleshPuppet;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.init.ModItems;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;


public class VineFleshPuppetSeedBall extends ThrowableItemProjectile implements ISoulEntity {

    public VineFleshPuppetSeedBall(EntityType<? extends VineFleshPuppetSeedBall> p_i50159_1_, Level p_i50159_2_) {
        super(p_i50159_1_, p_i50159_2_);
    }

    public VineFleshPuppetSeedBall(Level levelIn, LivingEntity throwerIn) {
        super(ModEntities.VINE_FLESH_BALL.get(), throwerIn, levelIn);
    }

    @Override
    protected @NotNull Item getDefaultItem() {
        return ModItems.ITEM_VINE.get();
    }

    @OnlyIn(Dist.CLIENT)
    private ParticleOptions getParticle() {
        ItemStack itemstack = this.getItemRaw();
        return itemstack.isEmpty() ? ParticleTypes.SMALL_FLAME : new ItemParticleOption(ParticleTypes.ITEM, itemstack);
    }


    @OnlyIn(Dist.CLIENT)
    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            ParticleOptions iparticledata = this.getParticle();

            for (int i = 0; i < 8; ++i) {
                this.level.addParticle(iparticledata, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
            }
        }

    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult blockHitResult) {
        //DevilRpg.LOGGER.debug("---onHitBlock");
        super.onHitBlock(blockHitResult);
        if (!this.level.isClientSide && this.getOwner() != null) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            PlayerSkillCapabilityInterface unwrappedPlayerCapability = IGenericCapability.getUnwrappedPlayerCapability((Player) this.getOwner(), PlayerSkillCapability.INSTANCE);
            //setLichen(this.level, (Player) this.getOwner(), blockPos, unwrappedPlayerCapability);
        }
    }

    /**
     * Called when the arrow hits an entity
     */
    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);
        Entity targetEntity = result.getEntity();

        if (!this.level.isClientSide) {
            if (targetEntity instanceof LivingEntity livingEntity) {
                PlayerSkillCapabilityInterface unwrappedPlayerCapability = IGenericCapability.getUnwrappedPlayerCapability((Player) this.getOwner(), PlayerSkillCapability.INSTANCE);
                int skillPoints = unwrappedPlayerCapability.getSkillsPoints().get(SkillEnum.VINEFLESHBALL);
                int amplifierLevel = Math.max(0, Math.min(skillPoints / 4, 4));
                int durationInTicks = 140 + skillPoints * 10; //7 -17
                MobEffectInstance instance = MobEffectVineFleshPuppet.createInstance(durationInTicks,amplifierLevel , (Player) getOwner());
                livingEntity.addEffect(instance);
                //((Player)getOwner()).addEffect(instance);
            }
        }

    }

    /**
     * Called when this Entity hits a block or entity.
     */

    @Override
    protected void onHit(@NotNull HitResult result) {
        super.onHit(result);
        if (!this.level.isClientSide) {
            this.level.broadcastEntityEvent(this, (byte) 3);
            //this.remove(RemovalReason.DISCARDED);
            this.discard();
        }
    }

    public void updateLevel(Player owner, SkillEnum callerSkillEnum) {
        this.setOwner(owner);
        /*LazyOptional<PlayerSkillCapabilityInterface> skill = owner.getCapability(PlayerSkillCapability.INSTANCE);
        if (skill.isPresent()) {
            int puntosAsignados = Objects.requireNonNull(skill.map(PlayerSkillCapabilityInterface::getSkillsPoints).orElse(null)).get(callerSkillEnum);
            float damage = puntosAsignados * 0.15F;
        }*/
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

}
