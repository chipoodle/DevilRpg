package com.chipoodle.devilrpg.capability;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.Capability.IStorage;

public class GenericCapabilityStorage<T extends IGenericCapability> implements IStorage<T> {

    @Override
    public INBT writeNBT(Capability<T> cpblt, T t, Direction drctn) {
        return t.getNBTData();
    }

    @Override
    public void readNBT(Capability<T> cpblt, T t, Direction drctn, INBT inbt) {
        t.setNBTData((CompoundNBT) inbt);
    }
}
