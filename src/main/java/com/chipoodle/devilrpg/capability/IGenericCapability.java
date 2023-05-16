package com.chipoodle.devilrpg.capability;

import com.chipoodle.devilrpg.entity.ITamableEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;

public interface IGenericCapability extends INBTSerializable<CompoundTag> {


    @SuppressWarnings("unchecked")
    static <T extends Player, U extends LazyOptional<V>, V extends IGenericCapability> V getUnwrappedPlayerCapability(T player, Capability<V> cap) {
        U capabilityInstance = (U) player.getCapability(cap);
        return capabilityInstance.orElse(null);
    }

    @SuppressWarnings("unchecked")
    static <T extends ITamableEntity, U extends LazyOptional<V>, V extends IGenericCapability> V getUnwrappedMinionCapability(T entity, Capability<V> cap) {
        U capabilityInstance = (U) entity.getCapability(cap);
        return capabilityInstance.orElse(null);
    }
}
