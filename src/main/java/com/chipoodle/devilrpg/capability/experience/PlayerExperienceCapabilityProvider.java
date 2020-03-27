package com.chipoodle.devilrpg.capability.experience;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;

import net.minecraft.util.Direction;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public class PlayerExperienceCapabilityProvider implements ICapabilitySerializable<INBT> {

    @CapabilityInject(IBaseExperienceCapability.class)
    public static Capability<IBaseExperienceCapability> EXPERIENCE_CAP = null;

    private final LazyOptional<IBaseExperienceCapability> instance = LazyOptional.of(()->EXPERIENCE_CAP.getDefaultInstance());

    public boolean hasCapability(Capability<?> capability, Direction side) {
        return capability == EXPERIENCE_CAP;
    }
    
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return capability == EXPERIENCE_CAP ? instance.cast() : LazyOptional.empty();
    }
    
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability) {
        return getCapability(capability,null);
    }
    
    @Override
    public INBT serializeNBT() {
        return instance.map(x -> x.getNBTData()).orElseGet(()-> new CompoundNBT());
    }

    @Override
    public void deserializeNBT(INBT t) {
        instance.ifPresent((cap) -> cap.setNBTData((CompoundNBT) t));
    }

}
