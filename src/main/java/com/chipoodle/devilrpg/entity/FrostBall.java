package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;


public class FrostBall extends ThrowableItemProjectile implements ISoulEntity {

    private float damage = 0;

    public FrostBall(EntityType<? extends FrostBall> p_i50159_1_, Level p_i50159_2_) {
        super(p_i50159_1_, p_i50159_2_);
    }

    public FrostBall(Level levelIn, LivingEntity throwerIn) {
        super(ModEntities.SOUL_ICEBALL.get(), throwerIn, levelIn);
    }

	/*public SoulIceBall(Level levelIn, double x, double y, double z) {
		super(ModEntities.SOUL_ICEBALL.get(), x, y, z, levelIn);
	}*/

    @Override
    protected @NotNull Item getDefaultItem() {
        return Items.SNOWBALL;
    }

    @OnlyIn(Dist.CLIENT)
    private ParticleOptions getParticle() {
        ItemStack itemstack = this.getItemRaw();
        // return (IParticleData) (itemstack.isEmpty() ? ParticleTypes.CLOUD
        return itemstack.isEmpty() ? ParticleTypes.SNOWFLAKE : new ItemParticleOption(ParticleTypes.ITEM, itemstack);
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

    /**
     * Called when the arrow hits an entity
     */
    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);
        Entity targetEntity = result.getEntity();
        targetEntity.hurt(this.damageSources().freeze(), damage);
    }

    /**
     * Called when this EntityFireball hits a block or entity.
     */

    @Override
    protected void onHit(@NotNull HitResult result) {
        super.onHit(result);
        if (!this.level.isClientSide) {
            this.level.broadcastEntityEvent(this, (byte) 3);
            this.remove(RemovalReason.DISCARDED);
        }
    }

    public void updateLevel(Player owner, SkillEnum callerSkillEnum) {
        this.setOwner(owner);
        LazyOptional<PlayerSkillCapabilityInterface> skill = owner.getCapability(PlayerSkillCapability.INSTANCE);
        if (skill != null && skill.isPresent()) {
            int puntosAsignados = Objects.requireNonNull(skill.map(PlayerSkillCapabilityInterface::getSkillsPoints).orElse(null)).get(callerSkillEnum);
            damage = puntosAsignados * 0.15F;
        }
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
