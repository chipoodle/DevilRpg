package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.entity.SoulBearEntity;
import com.chipoodle.devilrpg.entity.SoulWispEntity;
import com.chipoodle.devilrpg.entity.SoulWolfEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.GlobalEntityTypeAttributes;

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
public final class ModAttributes {

	public static void register() {
		/*Minecraft.getInstance().tell(() -> {
			GlobalEntityTypeAttributes.put(ModEntityTypes.SOUL_WOLF.get(), SoulWolfEntity.setAttributes().build());
			GlobalEntityTypeAttributes.put(ModEntityTypes.SOUL_BEAR.get(), SoulBearEntity.setAttributes().build());
			GlobalEntityTypeAttributes.put(ModEntityTypes.WISP.get(), SoulWispEntity.setAttributes().build());
		});*/

	}

}
