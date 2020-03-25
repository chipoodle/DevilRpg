package com.chipoodle.devilrpg.capability.skill;


import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;

public class PlayerSkillCapabilityProvider implements ICapabilityProvider, INBTSerializable<INBT> {

    @CapabilityInject(IBaseSkillCapability.class)
    public static Capability<IBaseSkillCapability> SKILL_CAP = null;

    private final LazyOptional<IBaseSkillCapability> instance = LazyOptional.of(()->SKILL_CAP.getDefaultInstance());

    public boolean hasCapability(Capability<?> capability, Direction side) {
        return capability == SKILL_CAP;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        return capability == SKILL_CAP ? instance.cast() : LazyOptional.empty();
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
