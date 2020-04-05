package com.chipoodle.devilrpg.capability.mana;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public class PlayerManaCapabilityProvider implements ICapabilitySerializable<INBT> {

    @CapabilityInject(IBaseManaCapability.class)
    public static Capability<IBaseManaCapability> MANA_CAP = null;

    private final LazyOptional<IBaseManaCapability> instance = LazyOptional.of(()->MANA_CAP.getDefaultInstance());

    public boolean hasCapability(Capability<?> capability, Direction side) {
        return capability == MANA_CAP;
    }
    
    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return capability == MANA_CAP ? instance.cast() : LazyOptional.empty();
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
