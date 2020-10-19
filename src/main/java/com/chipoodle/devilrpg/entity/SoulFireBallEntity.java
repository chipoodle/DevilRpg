package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.DamagingProjectileEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;
import net.minecraftforge.fml.network.FMLPlayMessages;
import net.minecraftforge.fml.network.NetworkHooks;

public class SoulFireBallEntity extends DamagingProjectileEntity {
	public int explosionPower = 1;
	private int puntosAsignados = 0;
	private float damage = 0;

	public SoulFireBallEntity(EntityType<? extends SoulFireBallEntity> p_i50163_1_, World p_i50163_2_) {
		super(p_i50163_1_, p_i50163_2_);
	}

	@OnlyIn(Dist.CLIENT)
	public SoulFireBallEntity(World worldIn, double x, double y, double z, double accelX, double accelY,
			double accelZ) {

		super(ModEntityTypes.SOUL_FIREBALL.get(), x, y, z, accelX, accelY, accelZ, worldIn);
	}

	public SoulFireBallEntity(World worldIn, LivingEntity shooter, double accelX, double accelY, double accelZ) {
		super(ModEntityTypes.SOUL_FIREBALL.get(), shooter, accelX, accelY, accelZ, worldIn);
	}

	/**
	 * Called when this EntityFireball hits a block or entity.
	 */
	protected void onImpact(RayTraceResult result) {
		super.onImpact(result);
		if (!this.world.isRemote) {
			if (result.getType() == RayTraceResult.Type.ENTITY) {
				Entity targetEntity = ((EntityRayTraceResult) result).getEntity();	
				targetEntity.attackEntityFrom(DamageSource.causeThrownDamage(this, this.shootingEntity), damage);
				this.applyEnchantments(this.shootingEntity, targetEntity);
			}
			this.remove();
		}

	}

	public void writeAdditional(CompoundNBT compound) {
		super.writeAdditional(compound);
		compound.putInt("ExplosionPower", this.explosionPower);
	}

	/**
	 * (abstract) Protected helper method to read subclass entity data from NBT.
	 */
	public void readAdditional(CompoundNBT compound) {
		super.readAdditional(compound);
		if (compound.contains("ExplosionPower", 99)) {
			this.explosionPower = compound.getInt("ExplosionPower");
		}

	}

	public void updateLevel(PlayerEntity owner) {
		LazyOptional<IBaseSkillCapability> skill = owner.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		if (skill != null && skill.isPresent()) {
			this.puntosAsignados = skill.map(x -> x.getSkillsPoints()).orElse(null).get(SkillEnum.FIREBALL);
			this.damage = puntosAsignados * 2;
		}
	}

	/**
	 * Called on the logical server to get a packet to send to the client containing
	 * data necessary to spawn your entity. Using Forge's method instead of the
	 * default vanilla one allows extra stuff to work such as sending extra data,
	 * using a non-default entity factory and having
	 * {@link IEntityAdditionalSpawnData} work.
	 *
	 * It is not actually necessary for our Entity to use Forge's method as
	 * it doesn't need any of this extra functionality, however, this is an example
	 * mod and many modders are unaware that Forge's method exists.
	 *
	 * @return The packet with data about your entity
	 * @see FMLPlayMessages.SpawnEntity
	 */
	@Override
	public IPacket<?> createSpawnPacket() {
		return NetworkHooks.getEntitySpawningPacket(this);
	}
}
