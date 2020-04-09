package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.soulwolf.SoulWolfEntity;
import com.chipoodle.devilrpg.entity.wildboar.WildBoarEntity;
import com.chipoodle.devilrpg.entity.wisp.WispEntity;

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

	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = new DeferredRegister<>(ForgeRegistries.ENTITIES,
			DevilRpg.MODID);

	public static final String WILD_BOAR_NAME = "wild_boar";
	public static final String SOUL_WOLF_NAME = "soul_wolf";
	public static final String WISP_NAME = "wisp";

	public static final RegistryObject<EntityType<WildBoarEntity>> WILD_BOAR = ENTITY_TYPES.register(WILD_BOAR_NAME,
			() -> EntityType.Builder.<WildBoarEntity>create(WildBoarEntity::new, EntityClassification.CREATURE)
					.size(EntityType.PIG.getWidth(), EntityType.PIG.getHeight())
					.build(new ResourceLocation(DevilRpg.MODID, WILD_BOAR_NAME).toString()));
	public static final RegistryObject<EntityType<SoulWolfEntity>> SOUL_WOLF = ENTITY_TYPES.register(SOUL_WOLF_NAME,
			() -> EntityType.Builder.<SoulWolfEntity>create(SoulWolfEntity::new, EntityClassification.CREATURE)
					.size(EntityType.WOLF.getWidth(), EntityType.WOLF.getHeight())
					.build(new ResourceLocation(DevilRpg.MODID, SOUL_WOLF_NAME).toString()));
	public static final RegistryObject<EntityType<WispEntity>> WISP = ENTITY_TYPES.register(WISP_NAME,
			() -> EntityType.Builder.<WispEntity>create(WispEntity::new, EntityClassification.CREATURE)
					.size(EntityType.PARROT.getWidth(), EntityType.PARROT.getHeight())
					.build(new ResourceLocation(DevilRpg.MODID, WISP_NAME).toString()));

}
