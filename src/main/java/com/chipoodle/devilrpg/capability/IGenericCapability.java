package com.chipoodle.devilrpg.capability;

import com.chipoodle.devilrpg.entity.ITamableEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.function.Supplier;

public interface IGenericCapability extends INBTSerializable<CompoundTag> {

    static <T extends Player, V extends IGenericCapability> V getUnwrappedPlayerCapability(T player, Supplier<AttachmentType<V>> cap) {
        return player.getData(cap);
    }

    static <T extends ITamableEntity, V extends IGenericCapability> V getUnwrappedMinionCapability(T entity, Supplier<AttachmentType<V>> cap) {
        return entity.getData(cap);
    }
}
