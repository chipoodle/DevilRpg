package com.chipoodle.devilrpg.capability.experience;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;

public class PlayerExperienceCapabilityStorage implements Capability.IStorage<IBaseExperienceCapability> {

    @Override
    public INBT writeNBT(Capability<IBaseExperienceCapability> cpblt, IBaseExperienceCapability t, Direction drctn) {
        return t.getNBTData();
    }

    @Override
    public void readNBT(Capability<IBaseExperienceCapability> cpblt, IBaseExperienceCapability t, Direction drctn, INBT inbt) {
        t.setNBTData((CompoundNBT) inbt);
    }
}
