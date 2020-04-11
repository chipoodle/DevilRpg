package com.chipoodle.devilrpg.capability.auxiliar;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public class PlayerAuxiliarCapabilityProvider implements ICapabilitySerializable<INBT> {

    @CapabilityInject(IBaseAuxiliarCapability.class)
    public static Capability<IBaseAuxiliarCapability> AUX_CAP = null;

    private final LazyOptional<IBaseAuxiliarCapability> instance = LazyOptional.of(()->AUX_CAP.getDefaultInstance());

    public boolean hasCapability(Capability<?> capability, Direction side) {
        return capability == AUX_CAP;
    }
    
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return capability == AUX_CAP ? instance.cast() : LazyOptional.empty();
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
