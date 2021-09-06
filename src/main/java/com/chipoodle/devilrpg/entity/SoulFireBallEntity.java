package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.capability.skill.IBaseSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityProvider;
import com.chipoodle.devilrpg.init.ModEntityTypes;
import com.chipoodle.devilrpg.util.SkillEnum;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.IPacket;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ItemParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
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

public class SoulFireBallEntity extends ProjectileItemEntity implements ISoulEntity {

	private int puntosAsignados = 0;
	private float damage = 0;

	public SoulFireBallEntity(EntityType<? extends SoulFireBallEntity> p_i50159_1_, World p_i50159_2_) {
		super(p_i50159_1_, p_i50159_2_);
	}

	public SoulFireBallEntity(World worldIn, LivingEntity throwerIn) {
		super(ModEntityTypes.SOUL_FIREBALL.get(), throwerIn, worldIn);
	}

	public SoulFireBallEntity(World worldIn, double x, double y, double z) {
		super(ModEntityTypes.SOUL_FIREBALL.get(), x, y, z, worldIn);
	}

	protected Item getDefaultItem() {
		return Items.SNOWBALL;
	}

	@OnlyIn(Dist.CLIENT)
	private IParticleData getParticle() {
		ItemStack itemstack = this.getItemRaw();
		// return (IParticleData) (itemstack.isEmpty() ? ParticleTypes.CLOUD
		return (IParticleData) (itemstack.isEmpty() ? ParticleTypes.ITEM_SNOWBALL
				: new ItemParticleData(ParticleTypes.ITEM, itemstack));
	}

	/**
	 * Handler for {@link World#setEntityState}
	 */
	@OnlyIn(Dist.CLIENT)
	public void handleEntityEvent(byte id) {
		if (id == 3) {
			IParticleData iparticledata = this.getParticle();

			for (int i = 0; i < 8; ++i) {
				this.level.addParticle(iparticledata, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
			}
		}

	}

	/**
	 * Called when the arrow hits an entity
	 */
	protected void onHitEntity(EntityRayTraceResult result) {
		super.onHitEntity(result);
		Entity targetEntity = result.getEntity();
		targetEntity.hurt(DamageSource.thrown(this, this.getOwner()), (float) damage / 6);
		if (targetEntity instanceof LivingEntity) {
			LivingEntity livingEntity = (LivingEntity) targetEntity;
			EffectInstance pri = new EffectInstance(Effects.MOVEMENT_SLOWDOWN, puntosAsignados * 6, getPotenciaPocion(puntosAsignados), false, true);
			if (livingEntity.canBeAffected(pri)) {
				livingEntity.addEffect(pri);
			}
		}
	}

	/**
	 * Called when this EntityFireball hits a block or entity.
	 */
	protected void onHit(RayTraceResult result) {
		super.onHit(result);
		if (!this.level.isClientSide) {
			this.level.broadcastEntityEvent(this, (byte) 3);
			this.remove();
		}
	}

	public void updateLevel(PlayerEntity owner) {
		LazyOptional<IBaseSkillCapability> skill = owner.getCapability(PlayerSkillCapabilityProvider.SKILL_CAP);
		if (skill != null && skill.isPresent()) {
			this.puntosAsignados = skill.map(x -> x.getSkillsPoints()).orElse(null).get(SkillEnum.FROSTBALL);
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
	 * It is not actually necessary for our Entity to use Forge's method as it
	 * doesn't need any of this extra functionality, however, this is an example mod
	 * and many modders are unaware that Forge's method exists.
	 *
	 * @return The packet with data about your entity
	 * @see FMLPlayMessages.SpawnEntity
	 */
	@Override
	public IPacket<?> getAddEntityPacket() {
		return NetworkHooks.getEntitySpawningPacket(this);
	}
}
