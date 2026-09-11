package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Holds a list of all our {@link EntityType}s. Suppliers that create
 * EntityTypes are added to the DeferredRegister. The DeferredRegister is then
 * added to our mod event bus in our constructor.
 *
 * @author Christian
 */
public final class ModEntities {

	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, DevilRpg.MODID);

	public static final String SOUL_WOLF_NAME = "soul_wolf";
	public static final String SOUL_BEAR_NAME = "soul_bear";
	private static final String WISP_HEALTH_NAME = "wisp_health";
	private static final String WISP_CURSE_NAME = "wisp_curse";
	public static final String WISP_BOMB_NAME = "wisp_bomb";
	public static final String WISP_ARCHER_NAME = "wisp_archer";
	private static final String WISP_RANGER_NAME = "wisp_ranger"; // guardabosque: fusion de chopper+forester
	public static final String SOUL_FROSTBALL_NAME = "soul_frostball";
	/** Lanza de hielo explosiva del pasivo nuevo del wisp arquero (3 por disparo especial). */
	public static final String ICE_SPEAR_NAME = "ice_spear";
	private static final String LICHEN_SEED_BALL_NAME = "lichen_seedball";
	private static final String VINE_FLESH_BALL_NAME = "vine_flesh_ball";
	private static final String SUNFLOWER_SHULKER_NAME = "sunflower_shulker";
	private static final String EXPLODING_SPORE_BULLET_NAME = "exploding_spore_bullet";
	private static final String GENERIC_ITEM_PROJECTILE_NAME = "generic_item_projectile";

	public static final String AGGRESSIVE_ZOMBIE_NAME = "aggressive_zombie";
	public static final String FROST_VEX_NAME = "frost_vex";
	public static final String SCULK_CULTIVATOR_NAME = "sculk_cultivator";


	public static final DeferredHolder<EntityType<?>, EntityType<SoulWolf>> SOUL_WOLF = ENTITY_TYPES.register(SOUL_WOLF_NAME,
			() -> EntityType.Builder.of(SoulWolf::new, MobCategory.CREATURE)
					.sized(EntityType.WOLF.getWidth(), EntityType.WOLF.getHeight())
					.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, SOUL_WOLF_NAME).toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<SoulBear>> SOUL_BEAR = ENTITY_TYPES.register(SOUL_BEAR_NAME,
			() -> EntityType.Builder.of(SoulBear::new, MobCategory.CREATURE)
					.sized(EntityType.POLAR_BEAR.getWidth(), EntityType.POLAR_BEAR.getHeight())
					.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, SOUL_BEAR_NAME).toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<SoulWispHealth>> WISP_HEALTH = ENTITY_TYPES.register(WISP_HEALTH_NAME,
			() -> EntityType.Builder.of(SoulWispHealth::new, MobCategory.CREATURE)
					.sized(EntityType.ALLAY.getWidth(), EntityType.ALLAY.getHeight())
					.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, WISP_HEALTH_NAME).toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<SoulWispArcher>> WISP_ARCHER = ENTITY_TYPES.register(WISP_ARCHER_NAME,
			() -> EntityType.Builder.of(SoulWispArcher::new, MobCategory.CREATURE)
			.sized(EntityType.ALLAY.getWidth(), EntityType.ALLAY.getHeight())
			.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, WISP_ARCHER_NAME).toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<SoulWispRanger>> WISP_RANGER = ENTITY_TYPES.register(WISP_RANGER_NAME,
			() -> EntityType.Builder.of(SoulWispRanger::new, MobCategory.CREATURE)
					.sized(EntityType.ALLAY.getWidth(), EntityType.ALLAY.getHeight())
					.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, WISP_RANGER_NAME).toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<FrostBall>> SOUL_FROSTBALL = ENTITY_TYPES.register(SOUL_FROSTBALL_NAME,
			() -> EntityType.Builder.<FrostBall>of(FrostBall::new, MobCategory.MISC)
					.sized(EntityType.WITHER_SKULL.getWidth(), EntityType.WITHER_SKULL.getHeight())
					.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, SOUL_FROSTBALL_NAME).toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<GenericItemProjectile>> GENERIC_ITEM_PROJECTILE = ENTITY_TYPES.register(GENERIC_ITEM_PROJECTILE_NAME,
			() -> EntityType.Builder.<GenericItemProjectile>of(GenericItemProjectile::new, MobCategory.MISC)
					.sized(EntityType.WITHER_SKULL.getWidth(), EntityType.WITHER_SKULL.getHeight())
					.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, GENERIC_ITEM_PROJECTILE_NAME).toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<LichenSeedBall>> LICHEN_SEED_BALL = ENTITY_TYPES.register(LICHEN_SEED_BALL_NAME,
			() -> EntityType.Builder.<LichenSeedBall>of(LichenSeedBall::new, MobCategory.MISC)
					.sized(EntityType.WITHER_SKULL.getWidth(), EntityType.WITHER_SKULL.getHeight())
					.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, LICHEN_SEED_BALL_NAME).toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<VineFleshPuppetSeedBall>> VINE_FLESH_BALL = ENTITY_TYPES.register(VINE_FLESH_BALL_NAME,
			() -> EntityType.Builder.<VineFleshPuppetSeedBall>of(VineFleshPuppetSeedBall::new, MobCategory.MISC)
					.sized(EntityType.WITHER_SKULL.getWidth(), EntityType.WITHER_SKULL.getHeight())
					.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, VINE_FLESH_BALL_NAME).toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<SunflowerShulker>> SUNFLOWER_SHULKER = ENTITY_TYPES.register(SUNFLOWER_SHULKER_NAME,
			() -> EntityType.Builder.of(SunflowerShulker::new, MobCategory.MISC)
					.sized(EntityType.SHULKER.getWidth(), EntityType.SHULKER.getHeight())
					.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, SUNFLOWER_SHULKER_NAME).toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<ExplodingSporeBullet>> EXPLODING_SPORE_BULLET = ENTITY_TYPES.register(EXPLODING_SPORE_BULLET_NAME,
			() -> EntityType.Builder.of(ExplodingSporeBullet::new, MobCategory.MISC)
					.sized(EntityType.BEE.getWidth(), EntityType.BEE.getHeight())
					.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, EXPLODING_SPORE_BULLET_NAME).toString()));


	public static final DeferredHolder<EntityType<?>, EntityType<AggressiveZombieEntity>> AGGRESSIVE_ZOMBIE = ENTITY_TYPES.register(AGGRESSIVE_ZOMBIE_NAME,
					() -> EntityType.Builder.of(AggressiveZombieEntity::new, MobCategory.MONSTER)
							.sized(0.6F, 1.95F) // Tamaño del mob
							.clientTrackingRange(8)
							.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, AGGRESSIVE_ZOMBIE_NAME).toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<FrostVexEntity>> FROST_VEX = ENTITY_TYPES.register(FROST_VEX_NAME,
					() -> EntityType.Builder.of(FrostVexEntity::new, MobCategory.MONSTER)
							.sized(EntityType.VEX.getWidth(), EntityType.VEX.getHeight())
							.clientTrackingRange(8)
							.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, FROST_VEX_NAME).toString()));

	public static final DeferredHolder<EntityType<?>, EntityType<SculkCultivatorEntity>> SCULK_CULTIVATOR = ENTITY_TYPES.register(SCULK_CULTIVATOR_NAME,
					() -> EntityType.Builder.of(SculkCultivatorEntity::new, MobCategory.MONSTER)
							.sized(0.6F, 1.95F)
							.clientTrackingRange(8)
							.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, SCULK_CULTIVATOR_NAME).toString()));

	/**
	 * Lanza de hielo del wisp arquero: un proyectil más grande que la bola de hielo, que persigue al enemigo
	 * más cercano y estalla con salpicadura (sin romper terreno). Es un proyectil, no un mob: categoría MISC.
	 */
	public static final DeferredHolder<EntityType<?>, EntityType<IceSpear>> ICE_SPEAR = ENTITY_TYPES.register(ICE_SPEAR_NAME,
			() -> EntityType.Builder.<IceSpear>of(IceSpear::new, MobCategory.MISC)
					.sized(0.4F, 0.4F)
					.clientTrackingRange(6)
					.updateInterval(2)
					.build(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, ICE_SPEAR_NAME).toString()));
}
