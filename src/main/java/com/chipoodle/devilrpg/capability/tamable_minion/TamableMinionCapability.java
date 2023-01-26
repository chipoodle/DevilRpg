package com.chipoodle.devilrpg.capability.tamable_minion;

import com.chipoodle.devilrpg.entity.ITameableEntity;
import com.chipoodle.devilrpg.skillsystem.skillinstance.MinionPassiveAttributes;
import net.minecraft.nbt.CompoundNBT;

public class TamableMinionCapability implements IBaseTamableMinionCapability {

	private CompoundNBT nbt = new CompoundNBT();
    private MinionPassiveAttributes minionPassiveAttributes;

    @Override
    public void applyPassives(ITameableEntity entity) {
        minionPassiveAttributes = new MinionPassiveAttributes(entity);
    }

    @Override
    public CompoundNBT getNBTData() {
        return nbt;
    }

    @Override
    public void setNBTData(CompoundNBT nbt) {
        this.nbt = nbt;
    }

}
