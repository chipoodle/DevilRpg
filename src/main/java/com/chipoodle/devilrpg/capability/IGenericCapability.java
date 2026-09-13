package com.chipoodle.devilrpg.capability;

import com.chipoodle.devilrpg.entity.ITamableEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.function.Supplier;

public interface IGenericCapability extends INBTSerializable<CompoundTag> {

    static <T extends Player, V extends IGenericCapability> V getUnwrappedPlayerCapability(T player, Supplier<AttachmentType<V>> cap) {
        // Null-safe a proposito: media mod llama aqui con getOwner(), que es null si el jugador no esta
        // disponible (otra dimension, desconectado, o justo al entrar al mundo). Antes eso era un
        // NullPointerException en toda la cara; los que llaman ya saben tratar el null.
        return player == null ? null : player.getData(cap);
    }

    static <T extends ITamableEntity, V extends IGenericCapability> V getUnwrappedMinionCapability(T entity, Supplier<AttachmentType<V>> cap) {
        return entity == null ? null : entity.getData(cap);
    }
}
