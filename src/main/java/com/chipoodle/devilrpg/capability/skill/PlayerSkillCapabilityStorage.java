package com.chipoodle.devilrpg.capability.skill;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.Capability.IStorage;

public class PlayerSkillCapabilityStorage implements IStorage<IBaseSkillCapability> {

    @Override
    public INBT writeNBT(Capability<IBaseSkillCapability> cpblt, IBaseSkillCapability t, Direction drctn) {
        return t.getNBTData();
    }

    @Override
    public void readNBT(Capability<IBaseSkillCapability> cpblt, IBaseSkillCapability t, Direction drctn, INBT inbt) {
        t.setNBTData((CompoundNBT) inbt);
    }
}
