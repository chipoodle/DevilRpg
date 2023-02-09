package com.chipoodle.devilrpg.capability.tamable_minion;

import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.entity.ITameableEntity;
import com.chipoodle.devilrpg.skillsystem.skillinstance.MinionPassiveAttributes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.CompoundTag;

public class TamableMinionCapabilityImplementation implements TamableMinionCapabilityInterface {

	private CompoundTag nbt = new CompoundTag();

    @Override
    public void applyPassives(ITameableEntity entity) {
        MinionPassiveAttributes minionPassiveAttributes = new MinionPassiveAttributes(entity);
    }

    @Override
    public CompoundTag serializeNBT() {
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        this.nbt = nbt;
    }

}
