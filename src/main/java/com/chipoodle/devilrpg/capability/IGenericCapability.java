package com.chipoodle.devilrpg.capability;

import com.chipoodle.devilrpg.entity.ITameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

public interface IGenericCapability {
	
	CompoundTag serializeNBT();

	void deserializeNBT(CompoundTag nbt);

	@SuppressWarnings("unchecked")
	static <T extends Player, U extends LazyOptional<V>, V extends IGenericCapability> V getUnwrappedPlayerCapability(
			T player, Capability<V> cap) {
		U capabilityInstance = (U) player.getCapability(cap);
		return capabilityInstance.isPresent() ? capabilityInstance.map(x -> x).get() : cap.getDefaultInstance();
	}

	static <T extends ITameableEntity, U extends LazyOptional<V>, V extends IGenericCapability> V getUnwrappedMinionCapability(
			T entity, Capability<V> cap) {
		U capabilityInstance = (U) entity.getCapability(cap);
		cap.orEmpty(,)
		return capabilityInstance.isPresent() ? capabilityInstance.map(x -> x).get() : cap.getDefaultInstance();
	}
}
