package com.chipoodle.devilrpg.capability.tamable_minion;


import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;

public class TamableMinionCapabilityProvider implements ICapabilityProvider {

    @CapabilityInject(IBaseTamableMinionCapability.class)
    public static Capability<IBaseTamableMinionCapability> TAMABLE_MINION_CAP = null;

    private final LazyOptional<IBaseTamableMinionCapability> instance = LazyOptional.of(()-> TAMABLE_MINION_CAP.getDefaultInstance());

    public boolean hasCapability(Capability<?> capability, Direction side) {
        return capability == TAMABLE_MINION_CAP;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return capability == TAMABLE_MINION_CAP ? instance.cast() : LazyOptional.empty();
    }
    
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability) {
        return getCapability(capability,null);
    }



}
