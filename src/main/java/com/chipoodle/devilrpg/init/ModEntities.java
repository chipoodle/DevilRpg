package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SoulBear;
import com.chipoodle.devilrpg.entity.FrostBall;
import com.chipoodle.devilrpg.entity.SoulWispArcher;
import com.chipoodle.devilrpg.entity.SoulWispBomber;
import com.chipoodle.devilrpg.entity.SoulWisp;
import com.chipoodle.devilrpg.entity.SoulWolf;


import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

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
 * @author Christian
 */
public final class ModEntities {

	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,DevilRpg.MODID);

	public static final String SOUL_WOLF_NAME = "soul_wolf";
	public static final String SOUL_BEAR_NAME = "soul_bear";
	public static final String WISP_NAME = "wisp";
	public static final String WISP_BOMB_NAME = "wisp_bomb";
	public static final String WISP_ARCHER_NAME = "wisp_archer";
	public static final String SOUL_ICEBALL_NAME = "soul_iceball";


	public static final RegistryObject<EntityType<SoulWolf>> SOUL_WOLF = ENTITY_TYPES.register(SOUL_WOLF_NAME,
			() -> EntityType.Builder.of(SoulWolf::new, MobCategory.CREATURE)
					.sized(EntityType.WOLF.getWidth(), EntityType.WOLF.getHeight())
					.build(new ResourceLocation(DevilRpg.MODID, SOUL_WOLF_NAME).toString()));

	public static final RegistryObject<EntityType<SoulBear>> SOUL_BEAR = ENTITY_TYPES.register(SOUL_BEAR_NAME,
			() -> EntityType.Builder.of(SoulBear::new, MobCategory.CREATURE)
					.sized(EntityType.POLAR_BEAR.getWidth(), EntityType.POLAR_BEAR.getHeight())
					.build(new ResourceLocation(DevilRpg.MODID, SOUL_BEAR_NAME).toString()));

	public static final RegistryObject<EntityType<SoulWisp>> WISP = ENTITY_TYPES.register(WISP_NAME,
			() -> EntityType.Builder.of(SoulWisp::new, MobCategory.CREATURE)
					.sized(EntityType.ALLAY.getWidth(), EntityType.ALLAY.getHeight())
					.build(new ResourceLocation(DevilRpg.MODID, WISP_NAME).toString()));

	public static final RegistryObject<EntityType<SoulWispBomber>> WISP_BOMB = ENTITY_TYPES.register(WISP_BOMB_NAME,
			() -> EntityType.Builder.of(SoulWispBomber::new, MobCategory.CREATURE)
			.sized(EntityType.ALLAY.getWidth(), EntityType.ALLAY.getHeight())
			.build(new ResourceLocation(DevilRpg.MODID, WISP_BOMB_NAME).toString()));
	
	public static final RegistryObject<EntityType<SoulWispArcher>> WISP_ARCHER = ENTITY_TYPES.register(WISP_ARCHER_NAME,
			() -> EntityType.Builder.of(SoulWispArcher::new, MobCategory.CREATURE)
			.sized(EntityType.ALLAY.getWidth(), EntityType.ALLAY.getHeight())
			.build(new ResourceLocation(DevilRpg.MODID, WISP_ARCHER_NAME).toString()));

	public static final RegistryObject<EntityType<FrostBall>> SOUL_ICEBALL = ENTITY_TYPES.register(SOUL_ICEBALL_NAME,
			() -> EntityType.Builder.<FrostBall>of(FrostBall::new, MobCategory.MISC)
					.sized(EntityType.WITHER_SKULL.getWidth(), EntityType.WITHER_SKULL.getHeight())
					.build(new ResourceLocation(DevilRpg.MODID, SOUL_ICEBALL_NAME).toString()));
}
