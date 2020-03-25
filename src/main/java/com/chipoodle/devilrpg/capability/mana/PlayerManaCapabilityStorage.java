package com.chipoodle.devilrpg.capability.mana;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;

public class PlayerManaCapabilityStorage implements Capability.IStorage<IBaseManaCapability> {

    @Override
    public INBT writeNBT(Capability<IBaseManaCapability> cpblt, IBaseManaCapability t, Direction drctn) {
        return t.getNBTData();
    }

    @Override
    public void readNBT(Capability<IBaseManaCapability> cpblt, IBaseManaCapability t, Direction drctn, INBT inbt) {
        t.setNBTData((CompoundNBT) inbt);
    }
}
