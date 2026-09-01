package com.chipoodle.devilrpg.capability.tamable_minion;

import net.minecraft.core.HolderLookup;
import com.chipoodle.devilrpg.entity.ITamableEntity;
import com.chipoodle.devilrpg.skillsystem.skillinstance.MinionPassiveAttributes;
import net.minecraft.nbt.CompoundTag;

public class TamableMinionCapabilityImplementation implements TamableMinionCapabilityInterface {

	private CompoundTag nbt = new CompoundTag();

    @Override
    public void applyPassives(ITamableEntity entity) {
        MinionPassiveAttributes minionPassiveAttributes = new MinionPassiveAttributes(entity);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        this.nbt = nbt;
    }

}
