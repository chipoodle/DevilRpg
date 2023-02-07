package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.entity.SoulIceBallEntity;
import com.chipoodle.devilrpg.entity.SoulWispArcherEntity;
import com.chipoodle.devilrpg.entity.SoulWispBomberEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;

import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Holds a list of all our {@link EntityType}s. Suppliers that create
 * EntityTypes are added to the DeferredRegister. The DeferredRegister is then
 * added to our mod event bus in our constructor. When the EntityType Registry
 * Event is fired by Forge and it is time for the mod to register its
 * EntityTypes, our EntityTypes are created and registered by the
 * DeferredRegister. The EntityType Registry Event will always be called after
 * the Block and Item registries are filled. Note: This supports registry
 * overrides.
 *
 * @author Cadiboo
 */
public final class ModEntityTypes {

	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITIES,DevilRpg.MODID);

	public static final String SOUL_WOLF_NAME = "soul_wolf";
	public static final String SOUL_BEAR_NAME = "soul_bear";
	public static final String WISP_NAME = "wisp";
	public static final String WISP_BOMB_NAME = "wisp_bomb";
	public static final String WISP_ARCHER_NAME = "wisp_archer";
	public static final String SOUL_ICEBALL_NAME = "soul_iceball";
	public static final String WEREWOLF_NAME = "werewolf";

	public static final RegistryObject<EntityType<SoulWolfEntity>> SOUL_WOLF = ENTITY_TYPES.register(SOUL_WOLF_NAME,
			() -> EntityType.Builder.of(SoulWolfEntity::new, EntityClassification.CREATURE)
					.sized(EntityType.WOLF.getWidth(), EntityType.WOLF.getHeight())
					.build(new ResourceLocation(DevilRpg.MODID, SOUL_WOLF_NAME).toString()));

	public static final RegistryObject<EntityType<SoulBearEntity>> SOUL_BEAR = ENTITY_TYPES.register(SOUL_BEAR_NAME,
			() -> EntityType.Builder.of(SoulBearEntity::new, EntityClassification.CREATURE)
					.sized(EntityType.POLAR_BEAR.getWidth(), EntityType.POLAR_BEAR.getHeight())
					.build(new ResourceLocation(DevilRpg.MODID, SOUL_BEAR_NAME).toString()));

	public static final RegistryObject<EntityType<SoulWispEntity>> WISP = ENTITY_TYPES.register(WISP_NAME,
			() -> EntityType.Builder.of(SoulWispEntity::new, EntityClassification.CREATURE)
					.sized(EntityType.BEE.getWidth(), EntityType.BEE.getHeight())
					.build(new ResourceLocation(DevilRpg.MODID, WISP_NAME).toString()));

	public static final RegistryObject<EntityType<SoulWispBomberEntity>> WISP_BOMB = ENTITY_TYPES.register(WISP_BOMB_NAME,
			() -> EntityType.Builder.of(SoulWispBomberEntity::new, EntityClassification.CREATURE)
			.sized(EntityType.BEE.getWidth(), EntityType.BEE.getHeight())
			.build(new ResourceLocation(DevilRpg.MODID, WISP_BOMB_NAME).toString()));
	
	public static final RegistryObject<EntityType<SoulWispArcherEntity>> WISP_ARCHER = ENTITY_TYPES.register(WISP_ARCHER_NAME,
			() -> EntityType.Builder.of(SoulWispArcherEntity::new, EntityClassification.CREATURE)
			.sized(EntityType.BEE.getWidth(), EntityType.BEE.getHeight())
			.build(new ResourceLocation(DevilRpg.MODID, WISP_ARCHER_NAME).toString()));

	public static final RegistryObject<EntityType<SoulIceBallEntity>> SOUL_ICEBALL = ENTITY_TYPES.register(SOUL_ICEBALL_NAME,
			() -> EntityType.Builder.<SoulIceBallEntity>of(SoulIceBallEntity::new, EntityClassification.MISC)
					.sized(EntityType.WITHER_SKULL.getWidth(), EntityType.WITHER_SKULL.getHeight())
					.build(new ResourceLocation(DevilRpg.MODID, SOUL_ICEBALL_NAME).toString()));
}
