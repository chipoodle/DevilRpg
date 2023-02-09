package com.chipoodle.devilrpg.capability.tamable_minion;

import com.chipoodle.devilrpg.entity.ITameableEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;


public interface TamableMinionCapabilityInterface extends INBTSerializable<CompoundTag> {

    void applyPassives(ITameableEntity entity);


}
