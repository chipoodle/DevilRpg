package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.init.ModEntities;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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


public class FrostBall extends ThrowableItemProjectile implements ISoulEntity {

    private float damage = 0;

    public FrostBall(EntityType<? extends FrostBall> p_i50159_1_, Level p_i50159_2_) {
        super(p_i50159_1_, p_i50159_2_);
    }

    public FrostBall(Level levelIn, LivingEntity throwerIn) {
        super(ModEntities.SOUL_FROSTBALL.get(), throwerIn, levelIn);
    }


    public void updateLevel(LivingEntity owner, int puntosAsignados) {
        this.setOwner(owner);
        damage = 1 + puntosAsignados * 0.17F;
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

    @Override
    protected void onHitBlock(@NotNull BlockHitResult blockHitResult) {
        super.onHitBlock(blockHitResult);
    }

    /**
     * Called when the arrow hits an entity
     */
    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        super.onHitEntity(result);
        Entity targetEntity = result.getEntity();
        targetEntity.hurt(this.damageSources().mobAttack((LivingEntity) getOwner()), damage);

        //targetEntity.hurt(this.damageSources().thrown(this, this.getOwner()), (float)damage);
        targetEntity.setIsInPowderSnow(true);
    }

    /**
     * Called when this EntityFireball hits a block or entity.
     */
    @Override
    protected void onHit(@NotNull HitResult result) {
        super.onHit(result);
        if (!this.level.isClientSide) {
            this.level.broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
